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
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The ban-on-login enforcement the {@code PlayerLoginEvent} (priority HIGHEST) listener consults before
 * player data loads (docs/09-deployment.md). For the connecting UUID/IP it checks, in order: a server-wide
 * lockdown (the broadest gate — only bypass-perm holders pass), then an active per-UUID tempban, then an
 * active IP ban on the connecting address. Any match is a kick-before-data-load.
 * Independently it runs alt-detection — the other UUIDs that have connected from this IP — and emits
 * {@code event=alt_detected} (allow/kick) for the audit trail, whether or not the login is refused.
 *
 * <p>Pure: the connecting identity, address and now instant are passed in, and the decision is returned as a
 * value the listener applies; the listener owns the Bukkit {@code PlayerLoginEvent} disallow call. The reason
 * text is pre-rendered through the {@link ModerationNotifier} so the kick screen shows a localized message.
 */
public final class LoginEnforcement {

    private final ModerationRepository repository;
    private final ModerationNotifier notifier;
    private final ModerationAudit audit;
    private final Clock clock;

    public LoginEnforcement(
            ModerationRepository repository, ModerationNotifier notifier, ModerationAudit audit, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Decide whether {@code who} connecting from {@code ip} may log in, recording seen and alt-detection.
     * {@code hasBypass} is whether the connecting player holds {@code uxmessentials.moderation.lockdown.bypass};
     * the listener resolves it from the live {@code Player} (a permission cannot be read for a connecting player
     * inside this pure use case) and threads it in so a locked-down server refuses every login but theirs.
     */
    public Decision evaluate(PlayerRef who, String ip, boolean hasBypass) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(ip, "ip");
        Instant now = clock.instant();
        if (repository.isLockedDown() && !hasBypass) {
            recordAndDetect(who, ip, now, true);
            return Decision.deny(render(who, ModerationMessageKey.MOD_LOCKDOWN_KICK, Map.of()));
        }
        Optional<Decision> tempban = tempbanKick(who, now);
        if (tempban.isPresent()) {
            recordAndDetect(who, ip, now, true);
            return tempban.get();
        }
        Optional<IpBan> ipBan = repository.activeIpBan(ip, now);
        if (ipBan.isPresent()) {
            recordAndDetect(who, ip, now, true);
            return Decision.deny(ipBanReason(who, ipBan.get()));
        }
        recordAndDetect(who, ip, now, false);
        return Decision.allow();
    }

    private Optional<Decision> tempbanKick(PlayerRef who, Instant now) {
        if (repository.loadTempban(who) instanceof TempbanState.Active ban && ban.isActiveAt(now)) {
            Map<String, String> ph = Map.of(
                    "duration", SanctionDuration.format(java.time.Duration.between(now, ban.until())),
                    "reason", ban.reason().orElse(""));
            return Optional.of(Decision.deny(render(who, ModerationMessageKey.TEMPBAN_KICK, ph)));
        }
        return Optional.empty();
    }

    private void recordAndDetect(PlayerRef who, String ip, Instant now, boolean kicked) {
        repository.recordSeen(who, Optional.of(ip), now);
        repository.recordIpSeen(who.uuid(), ip, now);
        // Alt-detection at login keys off the connecting address — the broadened /alts surface walks a
        // player's full history, but a login is a single address, so this stays the per-address lookup.
        List<UUID> alts = repository.altsByIp(ip, who.uuid());
        if (kicked || !alts.isEmpty()) {
            audit.altDetected(who.uuid(), ip, alts, kicked);
        }
    }

    private String ipBanReason(PlayerRef who, IpBan ban) {
        return render(
                who,
                ModerationMessageKey.BANIP_KICK,
                Map.of("reason", ban.reason().orElse("")));
    }

    private String render(PlayerRef who, MessageKey key, Map<String, String> ph) {
        return notifier.render(who, key, ph);
    }

    /**
     * The login decision the listener applies: {@code allowed} false carries the pre-rendered kick reason.
     *
     * @param allowed true to permit the login, false to refuse it
     * @param kickReason the rendered disconnect message when refused
     */
    public record Decision(boolean allowed, Optional<String> kickReason) {

        public Decision {
            Objects.requireNonNull(kickReason, "kickReason");
        }

        static Decision allow() {
            return new Decision(true, Optional.empty());
        }

        static Decision deny(String reason) {
            return new Decision(false, Optional.of(reason));
        }
    }
}
