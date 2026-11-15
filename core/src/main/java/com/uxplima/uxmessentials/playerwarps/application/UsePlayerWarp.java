package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpPasswordStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpBanStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpWhitelistStore;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.permission.DestinationPermissions;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.teleport.PaidTeleports;
import com.uxplima.uxmessentials.shared.application.teleport.PaidTeleports.Settlement;
import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.application.port.WarpSafetyChecker;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.jspecify.annotations.Nullable;

/**
 * {@code /pwarp <name>}: teleport a player to a player-warp resolved by its server-wide-unique name, through the
 * full ordered access gate. The gate runs six checks in a fixed precedence. <em>status → ban → membership →
 * access → safe-landing → cost</em>, and each check that a warp does not need short-circuits to a pass:
 *
 * <ol>
 *   <li><b>Status</b> refuses everyone, including the owner, when the warp is not
 *       {@link WarpStatus#ACTIVE ACTIVE}: a suspended or archived warp is settled by its owner before anyone
 *       teleports, so there is deliberately no bypass here.
 *   <li><b>Ban</b> refuses a banned actor, again including a banned owner, unless they hold the ban bypass.
 *       Ban precedes membership so a warp owner cannot ban-evade their own sanction, and a co-owner cannot
 *       walk past a ban an admin placed.
 *   <li><b>Membership</b> (owner, co-owner, or manager) admits past the access step <em>and</em> the cost step:
 *       a member never supplies a password, never needs to be whitelisted, and never pays. Members still face
 *       the safe-landing check.
 *   <li><b>Access</b>, for non-members only, branches on the warp's {@code WarpAccess}: PUBLIC passes, PASSWORD
 *       runs the rate-limited {@link #passwordStep}, WHITELIST checks the whitelist (or the whitelist bypass),
 *       PRIVATE refuses.
 *   <li><b>Safe-landing</b> refuses an unsafe destination unless the actor holds the safety bypass; it applies
 *       to members too, since a member is no safer landing in lava.
 *   <li><b>Cost</b> is the guarded last gate: only for a non-member, only for a priced warp, only when the actor
 *       lacks the cost bypass, and only when an economy provider is wired. It runs <em>after</em> safety so a
 *       payer is never debited for a hop that safety would refuse, and the visit and teleport happen only once
 *       it succeeds: a refused charge leaves no recorded visit and no hop.
 * </ol>
 *
 * <p>The visitor is charged before the warmup, but the owner is paid only once the visitor has landed. The teleport
 * can still end without moving them (a move during the warmup, a newer teleport, a missed hop, the player leaving),
 * and then the visitor gets the whole price back while the owner was never paid, so no refund has to reach into an
 * owner's bank that may already have been paid out. A landing whose owner share cannot be banked gives the visitor
 * their money back too, as a use whose payout fails always has: the money is never left with no one.
 *
 * <p>This use case never moves the player itself: once the gate passes it <em>delegates</em> execution to the
 * teleport context through {@link PlayerWarpTeleporter}, so the shared cooldown, the move-cancellable warmup,
 * and the region-aware async hop stay the teleport context's concern. Its exemptions end in {@code .pwarp}
 * under the shared {@code uxmessentials.bypass.} head, so every rule a staff member may skip is found in one place.
 *
 * <p>A warp tagged for another backend of the network takes a different post-charge route: rather than the local
 * hop it hands off to the shared cross-server hop through {@link PlayerWarpHops}, which records the intent and
 * connects the player across the proxy. Such a use first passes the teleport context's jail, combat and cooldown checks,
 * ahead of the cost step, since the engine's own launch gate never runs for it. The charge stays the gate's concern
 * and is passed through so a route that never leaves (the sub-group off, or the proxy unreachable) refunds exactly
 * what was debited.
 */
public final class UsePlayerWarp {

    private static final String BYPASS_BAN = DestinationPermissions.PWARP_BYPASS_BAN;
    private static final String BYPASS_PASSWORD = DestinationPermissions.PWARP_BYPASS_PASSWORD;
    private static final String BYPASS_WHITELIST = DestinationPermissions.PWARP_BYPASS_WHITELIST;
    private static final String BYPASS_SAFETY = DestinationPermissions.PWARP_BYPASS_SAFETY;
    private static final String BYPASS_COST = DestinationPermissions.PWARP_BYPASS_COST;

    private final PlayerWarpRepository repository;
    private final PlayerWarpTeleporter teleporter;
    private final Notifier notifier;
    private final WarpSafetyChecker safetyChecker;
    private final Permissions permissions;
    private final WarpBanStore banStore;
    private final WarpMemberStore memberStore;
    private final WarpWhitelistStore whitelistStore;
    private final PlayerWarpPasswordStore passwordStore;
    private final Cooldowns cooldowns;
    private final Optional<PlayerWarpEconomy> economy;
    private final String localServerId;
    private final PlayerWarpHops hops;
    private final Clock clock;
    private final PaidTeleports paid;
    private final Logger log;

    public UsePlayerWarp(
            PlayerWarpRepository repository,
            PlayerWarpTeleporter teleporter,
            Notifier notifier,
            WarpSafetyChecker safetyChecker,
            Permissions permissions,
            WarpBanStore banStore,
            WarpMemberStore memberStore,
            WarpWhitelistStore whitelistStore,
            PlayerWarpPasswordStore passwordStore,
            Cooldowns cooldowns,
            Optional<PlayerWarpEconomy> economy,
            String localServerId,
            PlayerWarpHops hops,
            Clock clock,
            PaidTeleports paid,
            Logger log) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.safetyChecker = Objects.requireNonNull(safetyChecker, "safetyChecker");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.banStore = Objects.requireNonNull(banStore, "banStore");
        this.memberStore = Objects.requireNonNull(memberStore, "memberStore");
        this.whitelistStore = Objects.requireNonNull(whitelistStore, "whitelistStore");
        this.passwordStore = Objects.requireNonNull(passwordStore, "passwordStore");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.localServerId = Objects.requireNonNull(localServerId, "localServerId");
        this.hops = Objects.requireNonNull(hops, "hops");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.paid = Objects.requireNonNull(paid, "paid");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Teleport {@code actor} to the warp {@code name} with no password supplied. */
    public Result<Unit, PlayerWarpError> useFor(PlayerRef actor, PlayerWarpName name) {
        return useFor(actor, name, Optional.empty());
    }

    /**
     * Teleport {@code actor} to the warp {@code name}, running the full access gate. A missing warp is rejected
     * with {@link PlayerWarpError#NOT_FOUND}; {@code password} carries the plaintext the actor typed for a
     * PASSWORD-access warp (empty when none was given, which the gate treats as a wrong attempt).
     */
    public Result<Unit, PlayerWarpError> useFor(PlayerRef actor, PlayerWarpName name, Optional<String> password) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(password, "password");
        Optional<PlayerWarp> warp = repository.findByName(name);
        if (warp.isEmpty()) {
            notifier.send(actor, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(PlayerWarpError.NOT_FOUND);
        }
        return admitAndGo(actor, warp.get(), password);
    }

    /**
     * The overload the command surface calls when it still resolves an explicit owner argument. Warp names are
     * globally unique, so the warp is found by name alone and {@code owner} is only a caller-side hint; the owner
     * the gate uses is always the resolved warp's own owner.
     */
    public Result<Unit, PlayerWarpError> useFor(PlayerRef actor, PlayerRef owner, PlayerWarpName name) {
        return useFor(actor, owner, name, Optional.empty());
    }

    /** As {@link #useFor(PlayerRef, PlayerRef, PlayerWarpName)} but carrying the entered password. */
    public Result<Unit, PlayerWarpError> useFor(
            PlayerRef actor, PlayerRef owner, PlayerWarpName name, Optional<String> password) {
        Objects.requireNonNull(owner, "owner");
        return useFor(actor, name, password);
    }

    private Result<Unit, PlayerWarpError> admitAndGo(PlayerRef actor, PlayerWarp warp, Optional<String> password) {
        Admission admission = gate(actor, warp, password);
        PlayerWarpError refusal = admission.refusal();
        if (refusal != null) {
            return admission.notified() ? Result.err(refusal) : refuse(actor, warp, refusal);
        }
        // The gate has passed and, for a priced non-member, already charged. If the warp lives on another backend,
        // the local hop would land nowhere, so route it across the proxy instead, carrying the exact charge so an
        // arrival failure refunds precisely.
        if (isRemote(warp)) {
            hops.send(actor, warp, admission.charged());
            return Result.ok();
        }
        // Opened before anything else can fail, so a fault between the charge and the launch gives the money back.
        Optional<Settlement> settlement = admission.charged().map(cost -> settlementFor(actor, warp, cost));
        try {
            notifier.send(
                    actor,
                    PlayerwarpsMessageKey.PWARP_TELEPORTING,
                    Map.of("warp", warp.name().value()));
            countVisit(warp);
            CompletionStage<TeleportOutcome> outcome = teleporter.teleportTo(actor, warp);
            settlement.ifPresent(open -> open.follow(outcome));
        } catch (RuntimeException failure) {
            settlement.ifPresent(Settlement::refuse);
            throw failure;
        }
        return Result.ok();
    }

    /**
     * Bump the visit counter in storage rather than read-modify-write here. The atomic increment avoids a
     * last-writer-wins race and needless cross-server cache invalidation. It runs only after the whole gate (including
     * the charge) has passed, so a refused teleport leaves no phantom visit. The count is a statistic the teleport
     * does not depend on, so a failed write costs the count, not the use.
     */
    private void countVisit(PlayerWarp warp) {
        try {
            repository.recordVisit(warp.id().orElseThrow());
        } catch (RuntimeException failure) {
            log.warn(
                    "event=pwarp_visit_not_counted warp={} reason={}",
                    warp.name().value(),
                    failure.toString());
        }
    }

    /**
     * Pay the owner their share once {@code actor} has landed, or give {@code actor} the whole price back when the
     * teleport did not land or the share could not be banked. Only a charge taken here reaches this, so a provider is
     * wired.
     */
    private Settlement settlementFor(PlayerRef actor, PlayerWarp warp, WarpCost cost) {
        PlayerWarpEconomy provider = economy.orElseThrow();
        return paid.open(
                PaidTeleports.subject("pwarp", warp.name().value(), actor, cost.amount(), cost.currencyId()),
                () -> provider.accrue(warp.id().orElseThrow(), cost.amount(), cost.currencyId())
                        .isOk(),
                () -> provider.refund(actor, cost.amount(), cost.currencyId()).isOk(),
                () -> notifier.send(
                        actor,
                        PlayerwarpsMessageKey.PWARP_CROSS_SERVER_REFUNDED,
                        Map.of("warp", warp.name().value())));
    }

    /** True when the warp is tagged for a backend other than this one, so it needs the cross-server route. */
    private boolean isRemote(PlayerWarp warp) {
        return warp.serverId().map(id -> !id.equals(localServerId)).orElse(false);
    }

    /**
     * Run the ordered gate, returning the first refusing {@link PlayerWarpError} or an admission carrying the exact
     * amount the cost step debited (empty when nothing was charged). Membership short-circuits both the access step
     * and the cost step; every other step applies uniformly.
     */
    private Admission gate(PlayerRef actor, PlayerWarp warp, Optional<String> password) {
        PlayerWarpError status = checkStatus(warp);
        if (status != null) {
            return Admission.refused(status);
        }
        PlayerWarpError ban = checkBan(actor, warp, clock.instant());
        if (ban != null) {
            return Admission.refused(ban);
        }
        boolean member = isMember(actor, warp);
        if (!member) {
            PlayerWarpError access = checkAccess(actor, warp, password);
            if (access != null) {
                return Admission.refused(access);
            }
        }
        PlayerWarpError safety = checkSafety(actor, warp);
        if (safety != null) {
            return Admission.refused(safety);
        }
        // The jail, combat and cooldown checks run here, before the charge, so a refused use costs the visitor
        // nothing and pays the owner nothing: a remote warp's hop leaves the engine's launch gate behind, and a
        // local launch that refused after the charge would keep the money.
        boolean admitted = isRemote(warp) ? hops.admit(actor, warp) : teleporter.admits(actor, warp);
        if (!admitted) {
            return Admission.refusedQuietly(PlayerWarpError.TELEPORT_REFUSED);
        }
        return member ? Admission.free() : charge(actor, warp);
    }

    /**
     * The gate outcome: a {@code refusal} (then {@code charged} is empty) or an admission carrying any charge taken.
     * {@code notified} marks a refusal whose reason the player has already been told.
     */
    private record Admission(@Nullable PlayerWarpError refusal, boolean notified, Optional<WarpCost> charged) {
        static Admission refused(PlayerWarpError error) {
            return new Admission(error, false, Optional.empty());
        }

        static Admission refusedQuietly(PlayerWarpError error) {
            return new Admission(error, true, Optional.empty());
        }

        static Admission free() {
            return new Admission(null, false, Optional.empty());
        }

        static Admission paid(WarpCost cost) {
            return new Admission(null, false, Optional.of(cost));
        }
    }

    /** Step 1: a warp that is not ACTIVE refuses everyone, with no bypass. */
    private @Nullable PlayerWarpError checkStatus(PlayerWarp warp) {
        return warp.status() == WarpStatus.ACTIVE ? null : PlayerWarpError.SUSPENDED;
    }

    /** Step 2: an in-force ban refuses the actor unless they hold the ban bypass. */
    private @Nullable PlayerWarpError checkBan(PlayerRef actor, PlayerWarp warp, Instant now) {
        boolean banned = banStore.isBannedAt(warp.id().orElseThrow(), actor.uuid(), now);
        if (banned && !permissions.has(actor, BYPASS_BAN)) {
            return PlayerWarpError.BANNED;
        }
        return null;
    }

    /** Step 3: the owner and any co-owner/manager are members; a member skips the access and cost steps. */
    private boolean isMember(PlayerRef actor, PlayerWarp warp) {
        return actor.uuid().equals(warp.owner().uuid())
                || memberStore.roleOf(warp.id().orElseThrow(), actor.uuid()).isPresent();
    }

    /** Step 4: the access branch, reached only for non-members. */
    private @Nullable PlayerWarpError checkAccess(PlayerRef actor, PlayerWarp warp, Optional<String> password) {
        return switch (warp.access()) {
            case PUBLIC -> null;
            case PASSWORD -> passwordStep(actor, warp, password);
            case WHITELIST -> whitelistStep(actor, warp);
            case PRIVATE -> PlayerWarpError.NOT_PUBLIC;
        };
    }

    /**
     * The PASSWORD access branch, rate-limited through the {@link Cooldowns} per-warp attempt label. The password
     * bypass skips it entirely. An active attempt cooldown refuses {@link PlayerWarpError#RATE_LIMITED} before any
     * match is tried; otherwise a correct password passes, and a wrong (or absent) one stamps the attempt cooldown
     * and refuses {@link PlayerWarpError#WRONG_PASSWORD}. The plaintext is only ever handed to the password store
     * to verify: it is never logged, rendered, or placed in a message placeholder.
     */
    private @Nullable PlayerWarpError passwordStep(PlayerRef actor, PlayerWarp warp, Optional<String> entered) {
        if (permissions.has(actor, BYPASS_PASSWORD)) {
            return null;
        }
        String label = attemptLabel(warp.id().orElseThrow());
        if (cooldowns.checkLabel(actor, label).isErr()) {
            return PlayerWarpError.RATE_LIMITED;
        }
        if (entered.isPresent() && passwordStore.matches(warp.id().orElseThrow(), entered.get())) {
            return null;
        }
        cooldowns.stampLabel(actor, label);
        return PlayerWarpError.WRONG_PASSWORD;
    }

    /** The WHITELIST access branch: on the list or holding the whitelist bypass passes; otherwise refuses. */
    private @Nullable PlayerWarpError whitelistStep(PlayerRef actor, PlayerWarp warp) {
        boolean allowed = whitelistStore.contains(warp.id().orElseThrow(), actor.uuid())
                || permissions.has(actor, BYPASS_WHITELIST);
        return allowed ? null : PlayerWarpError.NOT_WHITELISTED;
    }

    /** Step 5: an unsafe destination refuses unless the actor holds the safety bypass (applies to members too). */
    private @Nullable PlayerWarpError checkSafety(PlayerRef actor, PlayerWarp warp) {
        if (safetyChecker.isSafe(warp.location()) || permissions.has(actor, BYPASS_SAFETY)) {
            return null;
        }
        return PlayerWarpError.UNSAFE_LOCATION;
    }

    /**
     * Step 6: the guarded charge, reached only for a non-member. A free warp, the cost bypass, or an absent
     * economy provider all skip the debit and admit for free (the {@code WarpEconomy} soft-coupling precedent).
     * A priced warp with a provider present debits the visitor through {@link PlayerWarpEconomy#charge}, with nothing
     * accrued yet; any {@link ChargeError} refuses {@link PlayerWarpError#CANNOT_AFFORD} so no visit is recorded and
     * no hop occurs. A successful charge carries the exact price forward, so the owner is paid once the visitor lands
     * and the visitor is refunded exactly that when they do not.
     */
    private Admission charge(PlayerRef actor, PlayerWarp warp) {
        if (warp.price().amount().signum() <= 0 || permissions.has(actor, BYPASS_COST) || economy.isEmpty()) {
            return Admission.free();
        }
        // Either route can still be refunded after this charge (a local warmup can be cancelled, a remote hop can
        // miss), so the owner is paid only once the visitor has landed: the outcome of a local teleport settles it,
        // the cross-server hop settles a remote one.
        Result<Unit, ChargeError> charged =
                economy.get().charge(actor, warp.price().amount(), warp.price().currencyId());
        return charged.isErr() ? Admission.refused(PlayerWarpError.CANNOT_AFFORD) : Admission.paid(warp.price());
    }

    /** The per-warp password-attempt cooldown label, keyed by the warp's surrogate id. */
    private static String attemptLabel(PlayerWarpId id) {
        return "pwarp-password:" + id.value();
    }

    private Result<Unit, PlayerWarpError> refuse(PlayerRef actor, PlayerWarp warp, PlayerWarpError error) {
        notifier.send(actor, error.messageKey(), Map.of("warp", warp.name().value()));
        return Result.err(error);
    }
}
