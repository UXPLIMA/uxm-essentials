package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;

/**
 * The ban-on-login check, split along the threads it runs on. For the connecting UUID and address it weighs, in order:
 * a server-wide lockdown (the broadest gate, only bypass holders pass), then an active per-UUID tempban, then an
 * active ban on an account whose name differs from the login's only in case, then an active IP ban on the connecting
 * address, then, when the server refuses them, a new account under a case variant of a known name. Any match refuses
 * the login before player data loads.
 *
 * <p>The case variants matter on an offline-mode server only, where the uuid is derived from the exact spelling: a
 * banned {@code Steve} who logs in as {@code STEVE} arrives as another account with no ban of its own.
 * {@link CaseVariantSanctions} says which bans, mutes and jails follow the login there; it reads nothing while names
 * are not case-sensitive, where a case variant of a sanctioned name is somebody else.
 *
 * <ul>
 *   <li>{@link #read} fetches everything the check needs from storage. It runs at pre-login, off the tick, where no
 *       {@code Player} exists yet and so no permission can be read.
 *   <li>{@link #decide} weighs what was read against the two permission answers (the lockdown bypass and the
 *       punishment exemption). It touches no storage, so it runs at login, on the server thread, where the
 *       permissions are attached.
 *   <li>{@link #record} writes the alt-detection audit line once the outcome is known, and {@link #carryOver} gives
 *       an allowed account the mute and the jail it inherits, so every check that reads its own mute or jail, on this
 *       server and the others, holds it. Both write to storage, so they go back off the tick. The seen row is the
 *       join's to write: a refused login leaves none.
 * </ul>
 *
 * <p>The refusal text is rendered through the {@link Notifier}, so the disconnect screen is in the player's language.
 */
public final class LoginEnforcement {

    private final ModerationRepository repository;
    private final IpAlts ipAlts;
    private final Notifier notifier;
    private final ModerationAudit audit;
    private final Clock clock;
    private final CaseVariantSanctions caseVariants;
    private final CarryOver carryOver;
    private final boolean refuseCaseVariants;

    /** The check for a server whose logins are authenticated, where a name has one owner in any case. */
    public LoginEnforcement(
            ModerationRepository repository, IpAlts ipAlts, Notifier notifier, ModerationAudit audit, Clock clock) {
        this(repository, ipAlts, notifier, audit, clock, CaseVariantSanctions.none(repository), CarryOver.NONE, false);
    }

    /**
     * @param caseVariants the sanctions that follow a login from the other spellings of its name
     * @param carryOver gives the joining account the mute and the jail it inherits
     * @param refuseCaseVariants whether a new account under a case variant of a known name is refused even when no
     *     ban is involved
     */
    public LoginEnforcement(
            ModerationRepository repository,
            IpAlts ipAlts,
            Notifier notifier,
            ModerationAudit audit,
            Clock clock,
            CaseVariantSanctions caseVariants,
            CarryOver carryOver,
            boolean refuseCaseVariants) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ipAlts = Objects.requireNonNull(ipAlts, "ipAlts");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.caseVariants = Objects.requireNonNull(caseVariants, "caseVariants");
        this.carryOver = Objects.requireNonNull(carryOver, "carryOver");
        this.refuseCaseVariants = refuseCaseVariants;
    }

    /**
     * Read what decides whether {@code who} may log in from {@code ip}. It reads storage, so call it off the tick.
     *
     * <p>{@code ip} must be the player's own address (the forwarded one behind a proxy), the address the IP history
     * records and {@code /banip <player>} bans; the proxy's address would match nothing. The IP ban is read whether
     * or not the player turns out to be exempt, since the exemption is only known at login.
     */
    public Standing read(PlayerRef who, String ip) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(ip, "ip");
        Instant now = clock.instant();
        return new Standing(
                who,
                ip,
                repository.isLockedDown(),
                repository.loadTempban(who),
                repository.activeIpBan(ip, now),
                ipAlts.onAddress(ip, who.uuid()),
                caseVariants.atLogin(who, ip, now));
    }

    /**
     * Give {@code standing}'s account the mute and the jail it inherits from another spelling of its name, through the
     * {@link CarryOver} the server was built with. Call it only for a login that was allowed, so a refused one leaves
     * nothing behind. It writes to storage, so call it off the tick.
     */
    public void carryOver(Standing standing) {
        Objects.requireNonNull(standing, "standing");
        carryOver.carryOver(standing.who(), standing.inherited());
    }

    /**
     * Decide from {@code standing} whether its player may log in. {@code hasBypass} is whether they hold
     * {@code uxmessentials.bypass.lockdown}, so a locked-down server refuses every login but theirs. {@code exempt}
     * is whether they hold {@link ModerationGuard#EXEMPT_NODE}: an exempt holder passes an IP ban on their address,
     * as the live IP ban kick spares them. Reads no storage.
     */
    public Decision decide(Standing standing, boolean hasBypass, boolean exempt) {
        Objects.requireNonNull(standing, "standing");
        PlayerRef who = standing.who();
        Instant now = clock.instant();
        if (standing.lockedDown() && !hasBypass) {
            return Decision.deny(render(who, ModerationMessageKey.MOD_LOCKDOWN_KICK, Map.of()));
        }
        if (standing.tempban() instanceof TempbanState.Active ban && ban.isActiveAt(now)) {
            return Decision.deny(BanScreen.of(ban, now).render(notifier, who));
        }
        // The same person under another spelling: the ban follows them, exempt or not, as it does on their own uuid.
        if (standing.inherited().ban() instanceof TempbanState.Active ban && ban.isActiveAt(now)) {
            return Decision.deny(BanScreen.of(ban, now).render(notifier, who));
        }
        Optional<IpBan> ipBan = standing.ipBan().filter(ban -> !exempt && ban.isActiveAt(now));
        if (ipBan.isPresent()) {
            IpBan ban = ipBan.get();
            return Decision.deny(render(
                    who,
                    ModerationMessageKey.BANIP_KICK,
                    Map.of(
                            "reason",
                            SanctionReasons.read(notifier::render, who, ban.reason(), ban.drawnReason())
                                    .orElse(""))));
        }
        // An account that already joined keeps joining: the option stops new look-alikes, it does not pick between
        // accounts the server already has.
        CaseVariantSanctions.Inherited inherited = standing.inherited();
        if (refuseCaseVariants && !inherited.known() && !inherited.variants().isEmpty()) {
            return Decision.deny(render(
                    who,
                    ModerationMessageKey.MOD_LOGIN_NAME_VARIANT,
                    Map.of("player", who.name(), "matches", spellings(inherited.variants()))));
        }
        return Decision.allow();
    }

    private static String spellings(List<PlayerRef> variants) {
        TreeSet<String> names = new TreeSet<>();
        for (PlayerRef variant : variants) {
            names.add(variant.name());
        }
        return String.join(", ", names);
    }

    /**
     * Audit the other accounts on {@code standing}'s address when there are any or the login was {@code refused}. It
     * writes to storage, so call it off the tick.
     */
    public void record(Standing standing, boolean refused) {
        Objects.requireNonNull(standing, "standing");
        // The seen row, first seen included, is written by the join, not here: a refused login must leave no trace a
        // sanction on another spelling of the name would count as an account that existed before it.
        // The address history itself is written by the kernel recorder on join. Alt detection here keys off the
        // connecting address: /alts walks a player's whole history, but a login is one address.
        if (refused || !standing.alts().isEmpty()) {
            audit.altDetected(standing.who().uuid(), standing.ip(), standing.alts(), refused);
        }
    }

    private String render(PlayerRef who, MessageKey key, Map<String, String> ph) {
        return notifier.render(who, key, ph);
    }

    /** Gives a joining account the mute and the jail it inherits ({@link CaseVariantCopies#carryOver}). */
    @FunctionalInterface
    public interface CarryOver {

        /** Nothing is ever inherited: the answer for a server whose logins are authenticated. */
        CarryOver NONE = (who, inherited) -> {};

        void carryOver(PlayerRef who, CaseVariantSanctions.Inherited inherited);
    }

    /**
     * What storage said about one login: whether the server is locked down, the player's tempban, the IP ban on
     * their address, the other accounts seen on that address, and, on an offline-mode server, what the login inherits
     * from the other spellings of its name.
     */
    public record Standing(
            PlayerRef who,
            String ip,
            boolean lockedDown,
            TempbanState tempban,
            Optional<IpBan> ipBan,
            List<UUID> alts,
            CaseVariantSanctions.Inherited inherited) {

        public Standing {
            Objects.requireNonNull(who, "who");
            Objects.requireNonNull(ip, "ip");
            Objects.requireNonNull(tempban, "tempban");
            Objects.requireNonNull(ipBan, "ipBan");
            alts = List.copyOf(alts);
            Objects.requireNonNull(inherited, "inherited");
        }
    }

    /**
     * The login decision the listener applies: {@code allowed} false carries the rendered disconnect screen.
     * {@code retry} marks a refusal that is not a sanction: the check could not be made, and joining again a moment
     * later may succeed.
     *
     * @param allowed true to permit the login, false to refuse it
     * @param retry true when the refusal asks the player to join again rather than barring them
     * @param kickReason the rendered disconnect message when refused
     */
    public record Decision(boolean allowed, boolean retry, Optional<String> kickReason) {

        public Decision {
            Objects.requireNonNull(kickReason, "kickReason");
        }

        static Decision allow() {
            return new Decision(true, false, Optional.empty());
        }

        static Decision deny(String reason) {
            return new Decision(false, false, Optional.of(reason));
        }

        static Decision retryLater(String reason) {
            return new Decision(false, true, Optional.of(reason));
        }
    }
}
