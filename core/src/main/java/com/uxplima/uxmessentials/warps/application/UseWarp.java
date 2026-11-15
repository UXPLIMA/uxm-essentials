package com.uxplima.uxmessentials.warps.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.permission.DestinationPermissions;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.teleport.PaidTeleports;
import com.uxplima.uxmessentials.shared.application.teleport.PaidTeleports.Settlement;
import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpSafetyChecker;
import com.uxplima.uxmessentials.warps.application.port.WarpTeleporter;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.Nullable;

/**
 * {@code /warp <name>}: teleport a player to a server warp. The warp is resolved by name and the player is run
 * through every check in order: the warp's permissions, its lock, its password, the safety of the destination, and
 * only then the cost. Execution is then <em>delegated</em> to the teleport context through {@link WarpTeleporter}:
 * this use case never moves the player itself, so the shared cooldown, the move-cancellable warmup and the
 * region-aware async hop are the teleport context's concern.
 *
 * <p>The jail, combat and cooldown checks of the teleport run before the charge too, so a use they refuse costs
 * nothing. The charge is still taken before the warmup, so a use whose teleport then never lands (a move during the
 * warmup, a newer teleport, a missed hop, the player leaving) gets it back once, through {@link PaidTeleports}.
 *
 * <p>A warp set on another backend, while the warps {@code cross-server} block is on, is handed to the shared
 * cross-server hop through {@link WarpHops} instead, on the async lane, since the hop writes to the shared database.
 * The teleport rules and the charge run there, in that order; the destination's safety is checked on the target,
 * the only backend that holds its world, when the player lands. The charge travels with the hop and comes back on
 * every miss.
 *
 * <p>With no economy provider wired, a priced warp's cost is ignored and the warp is usable for free: the soft
 * coupling to the economy context.
 */
public final class UseWarp {

    /** Lets a player use a warp whose destination fails the safety check, here and on a landing elsewhere. */
    static final String BYPASS_SAFETY = DestinationPermissions.WARP_BYPASS_SAFETY;

    private final WarpRepository repository;
    private final WarpAccess access;
    private final WarpTeleporter teleporter;
    private final Notifier notifier;
    private final WarpSafetyChecker safetyChecker;
    private final Permissions permissions;
    private final Scheduler scheduler;
    private final PlayerLookup players;
    private final WarpHops hops;
    private final PaidTeleports paid;
    private final Logger log;

    public UseWarp(
            WarpRepository repository,
            WarpAccess access,
            WarpTeleporter teleporter,
            Notifier notifier,
            WarpSafetyChecker safetyChecker,
            Permissions permissions,
            Scheduler scheduler,
            PlayerLookup players,
            WarpHops hops,
            PaidTeleports paid,
            Logger log) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.access = Objects.requireNonNull(access, "access");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.safetyChecker = Objects.requireNonNull(safetyChecker, "safetyChecker");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.players = Objects.requireNonNull(players, "players");
        this.hops = Objects.requireNonNull(hops, "hops");
        this.paid = Objects.requireNonNull(paid, "paid");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Teleport {@code who} to the warp {@code name} themselves, gating access and cost first. */
    public Result<Unit, WarpError> use(PlayerRef who, WarpName name) {
        return useFor(who, who, name, Optional.empty());
    }

    public Result<Unit, WarpError> use(PlayerRef who, WarpName name, String password) {
        return useFor(who, who, name, Optional.of(password));
    }

    /**
     * Send {@code recipient} to the warp {@code name}, triggered by {@code actor} (a staff send when the two
     * differ). The access gate and the optional cost apply to the <em>recipient</em>, staff send another
     * player only to a warp that player may use, and the recipient is the one teleported. On a cross-player
     * success the actor is told the send went through; a self-send (actor == recipient) takes the usual
     * teleporting path with no extra notice.
     */
    public Result<Unit, WarpError> useFor(PlayerRef actor, PlayerRef recipient, WarpName name) {
        return useFor(actor, recipient, name, Optional.empty());
    }

    public Result<Unit, WarpError> useFor(
            PlayerRef actor, PlayerRef recipient, WarpName name, Optional<String> password) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(name, "name");
        Optional<Warp> warp = repository.find(name);
        if (warp.isEmpty()) {
            notifier.send(actor, WarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
            return Result.err(WarpError.NOT_FOUND);
        }
        return admitAndGo(actor, recipient, warp.get(), password);
    }

    private Result<Unit, WarpError> admitAndGo(
            PlayerRef actor, PlayerRef recipient, Warp warp, Optional<String> enteredPassword) {
        Map<String, String> placeholders = Map.of("warp", warp.name().value());
        WarpError refusal = refusal(recipient, warp, enteredPassword);
        if (refusal != null) {
            notifier.send(actor, refusal.messageKey(), placeholders);
            return Result.err(refusal);
        }
        if (hops.routes(warp)) {
            return dispatchRemote(actor, recipient, warp);
        }
        if (!teleporter.admits(recipient, warp)) {
            return teleportRefused(actor, recipient, warp);
        }
        Result<Optional<WarpCost>, WarpError> charged = takeCharge(actor, recipient, warp);
        if (charged.isErr()) {
            return Result.err(charged.errorOrThrow());
        }
        // Opened before anything else can fail, so a fault between the charge and the launch gives the money back
        // instead of leaving it taken for a teleport that never started.
        Optional<Settlement> settlement = charged.orElseThrow().map(cost -> settlementFor(recipient, warp, cost));
        try {
            tellSent(actor, recipient, warp);
            notifier.send(recipient, WarpsMessageKey.WARP_TELEPORTING, placeholders);
            // The in-memory warp already carries the new visitor count for the hop below.
            Warp updated = warp.incrementedVisitors();
            countVisit(updated);
            CompletionStage<TeleportOutcome> outcome = teleporter.teleportTo(recipient, updated);
            settlement.ifPresent(open -> open.follow(outcome));
        } catch (RuntimeException failure) {
            settlement.ifPresent(Settlement::refuse);
            throw failure;
        }
        return Result.ok();
    }

    /**
     * Persist the visitor counter off the caller's thread, since the write goes to the database. The counter is a
     * display statistic the teleport does not depend on, so a lane that refuses it costs the count, not the use.
     */
    private void countVisit(Warp updated) {
        try {
            scheduler.async(() -> repository.save(updated));
        } catch (RuntimeException refused) {
            log.warn(
                    "event=warp_visit_not_counted warp={} reason={}",
                    updated.name().value(),
                    refused.toString());
        }
    }

    /** Give {@code recipient} back what the use cost them if the teleport does not land, and tell them once. */
    private Settlement settlementFor(PlayerRef recipient, Warp warp, WarpCost cost) {
        return paid.open(
                PaidTeleports.subject("warp", warp.name().value(), recipient, cost.amount(), cost.currencyId()),
                () -> access.refund(recipient, cost),
                () -> notifier.send(
                        recipient,
                        WarpsMessageKey.WARP_CROSS_SERVER_REFUNDED,
                        Map.of("warp", warp.name().value())));
    }

    /**
     * Hand a use of a warp on another backend to the async lane: the hop records its pending row in the shared
     * database, so none of it may run on the command, menu or sign thread that called. Nothing has been charged
     * yet, so a lane that refuses the task only has to say the warp cannot be reached.
     */
    private Result<Unit, WarpError> dispatchRemote(PlayerRef actor, PlayerRef recipient, Warp warp) {
        try {
            scheduler.async(() -> sendRemote(actor, recipient, warp));
            return Result.ok();
        } catch (RuntimeException refused) {
            notifier.send(
                    recipient,
                    WarpsMessageKey.WARP_CROSS_SERVER_UNAVAILABLE,
                    Map.of(
                            "warp",
                            warp.name().value(),
                            "server",
                            warp.knownServer().orElse("")));
            return Result.err(WarpError.TELEPORT_REFUSED);
        }
    }

    /**
     * Off-tick: the teleport rules, then the charge, then the hop that carries it. A player who left before the task
     * ran is neither charged nor sent: the connect could not reach them, and the charge would wait out the TTL.
     */
    private void sendRemote(PlayerRef actor, PlayerRef recipient, Warp warp) {
        if (!players.isOnline(recipient.uuid())) {
            return;
        }
        if (!hops.admit(recipient, warp)) {
            teleportRefused(actor, recipient, warp);
            return;
        }
        Result<Optional<WarpCost>, WarpError> charged = charge(actor, recipient, warp);
        if (charged.isOk()) {
            hops.send(recipient, warp, charged.orElseThrow());
        }
    }

    /** Charge the use to {@code recipient}, telling {@code actor} a failed charge or a staff send that went through. */
    private Result<Optional<WarpCost>, WarpError> charge(PlayerRef actor, PlayerRef recipient, Warp warp) {
        Result<Optional<WarpCost>, WarpError> charged = takeCharge(actor, recipient, warp);
        if (charged.isOk()) {
            tellSent(actor, recipient, warp);
        }
        return charged;
    }

    /** Charge the use to {@code recipient}, telling {@code actor} when the charge failed. */
    private Result<Optional<WarpCost>, WarpError> takeCharge(PlayerRef actor, PlayerRef recipient, Warp warp) {
        Result<Optional<WarpCost>, WarpError> charged = access.charge(recipient, warp);
        if (charged.isErr()) {
            notifier.send(
                    actor,
                    charged.errorOrThrow().messageKey(),
                    Map.of("warp", warp.name().value()));
        }
        return charged;
    }

    /** Tell a staff member who sent another player that the send went through. */
    private void tellSent(PlayerRef actor, PlayerRef recipient, Warp warp) {
        if (!actor.uuid().equals(recipient.uuid())) {
            notifier.send(
                    actor, WarpsMessageKey.WARP_SENT, Map.of("warp", warp.name().value(), "player", recipient.name()));
        }
    }

    /**
     * The teleport rules refused the use and have told {@code recipient} why; a staff member who sent them is told
     * the send did not go through, since those rules speak only to the player they stop.
     */
    private Result<Unit, WarpError> teleportRefused(PlayerRef actor, PlayerRef recipient, Warp warp) {
        if (!actor.uuid().equals(recipient.uuid())) {
            notifier.send(
                    actor,
                    WarpsMessageKey.WARP_SENT_REFUSED,
                    Map.of("warp", warp.name().value(), "player", recipient.name()));
        }
        return Result.err(WarpError.TELEPORT_REFUSED);
    }

    /** The first check {@code recipient} fails before any money moves, or null when they pass them all. */
    private @Nullable WarpError refusal(PlayerRef recipient, Warp warp, Optional<String> enteredPassword) {
        if (!access.permits(recipient, warp)) {
            return WarpError.NO_PERMISSION;
        }
        if (warp.isLocked() && !permissions.has(recipient, DestinationPermissions.WARP_BYPASS_LOCK)) {
            return WarpError.LOCKED;
        }
        if (warp.password().isPresent()
                && !permissions.has(recipient, DestinationPermissions.WARP_BYPASS_PASSWORD)
                && !enteredPassword.equals(warp.password())) {
            return WarpError.WRONG_PASSWORD;
        }
        // A warp on another backend points into a world this one does not hold, so only the target can judge it.
        if (!hops.routes(warp)
                && !safetyChecker.isSafe(warp.location())
                && !permissions.has(recipient, BYPASS_SAFETY)) {
            return WarpError.UNSAFE_LOCATION;
        }
        return null;
    }
}
