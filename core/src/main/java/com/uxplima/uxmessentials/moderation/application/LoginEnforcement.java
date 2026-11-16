package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 * active IP ban on the connecting address. Any match refuses the login before player data loads.
 *
 * <ul>
 *   <li>{@link #read} fetches everything the check needs from storage. It runs at pre-login, off the tick, where no
 *       {@code Player} exists yet and so no permission can be read.
 *   <li>{@link #decide} weighs what was read against the two permission answers (the lockdown bypass and the
 *       punishment exemption). It touches no storage, so it runs at login, on the server thread, where the
 *       permissions are attached.
 *   <li>{@link #record} writes the last-seen row and the alt-detection audit line once the outcome is known. It
 *       writes to storage, so it goes back off the tick.
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

    public LoginEnforcement(
            ModerationRepository repository, IpAlts ipAlts, Notifier notifier, ModerationAudit audit, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ipAlts = Objects.requireNonNull(ipAlts, "ipAlts");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
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
                ipAlts.onAddress(ip, who.uuid()));
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
        return Decision.allow();
    }

    /**
     * Record that {@code standing}'s player tried to log in, and audit the other accounts on their address when there
     * are any or the login was {@code refused}. It writes to storage, so call it off the tick.
     */
    public void record(Standing standing, boolean refused) {
        Objects.requireNonNull(standing, "standing");
        repository.recordSeen(standing.who(), Optional.of(standing.ip()), clock.instant());
        // The address history itself is written by the kernel recorder on join. Alt detection here keys off the
        // connecting address: /alts walks a player's whole history, but a login is one address.
        if (refused || !standing.alts().isEmpty()) {
            audit.altDetected(standing.who().uuid(), standing.ip(), standing.alts(), refused);
        }
    }

    private String render(PlayerRef who, MessageKey key, Map<String, String> ph) {
        return notifier.render(who, key, ph);
    }

    /**
     * What storage said about one login: whether the server is locked down, the player's tempban, the IP ban on
     * their address, and the other accounts seen on that address.
     */
    public record Standing(
            PlayerRef who,
            String ip,
            boolean lockedDown,
            TempbanState tempban,
            Optional<IpBan> ipBan,
            List<UUID> alts) {

        public Standing {
            Objects.requireNonNull(who, "who");
            Objects.requireNonNull(ip, "ip");
            Objects.requireNonNull(tempban, "tempban");
            Objects.requireNonNull(ipBan, "ipBan");
            alts = List.copyOf(alts);
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
