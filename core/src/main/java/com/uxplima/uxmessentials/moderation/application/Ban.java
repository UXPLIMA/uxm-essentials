package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionBroadcast;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.AddressStrictness;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerTempbanned;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * {@code /ban <player> [reason]}: a permanent UUID ban. A permanent ban reuses the tempban row — it is a
 * {@link TempbanState.Active} whose expiry is so far out ({@link #PERMANENT_SPAN}) that the ban-on-login
 * listener's {@code isActiveAt} check stays true for any realistic reconnection, so the existing login
 * enforcement bars the player unchanged. The expiry is a far-future sentinel computed from the clock rather
 * than {@code Instant.MAX} so the value still fits the epoch-milli column the tempban row stores. An exempt
 * target is refused; on success an online target is kicked immediately, {@code PlayerTempbanned} is published
 * and the action is audit-logged with the {@code permanent} label.
 *
 * <p>A permanent ban requires the unlimited duration tier: the requested span is the far-future
 * {@link #PERMANENT_SPAN}, so an actor whose {@code ban.maxduration} cap is finite has the ban reduced to that
 * cap (a timed ban) and is told {@code MOD_DURATION_CAPPED}. An actor with no cap (the default) keeps the
 * permanent ban. Unless the ban is silent ({@code /ban -s}), the staff broadcast announces it.
 *
 * <p>Address-strictness: under {@link AddressStrictness#STRICT} (opt-in; default {@link AddressStrictness#NORMAL}
 * is zero behaviour change) a successful UUID ban additionally IP-bans every address the target is known to
 * have connected from, so a banned account cannot return on a fresh one from the same connection. The fan-out
 * is fail-safe — if no IP is on record the UUID ban still succeeds — and never runs against an exempt target
 * (the exempt gate already refused them above; this is a second guard so STRICT never IP-bans an exempt
 * player's shared connection collaterally).
 */
public final class Ban {

    /** The far-future span that makes a tempban effectively permanent without overflowing the stored expiry. */
    static final Duration PERMANENT_SPAN = Duration.ofDays(365_000L);

    private static final String PERMANENT_LABEL = "permanent";

    private final ModerationRepository repository;
    private final Sanctions sanctions;
    private final ModerationGuard guard;
    private final ModerationNotifier notifier;
    private final ModerationAudit audit;
    private final DomainEventPublisher events;
    private final SanctionHistoryRecorder history;
    private final SanctionDurationLimit limit;
    private final SanctionBroadcast broadcast;
    private final AddressStrictness strictness;
    private final Clock clock;

    public Ban(
            ModerationRepository repository,
            Sanctions sanctions,
            ModerationGuard guard,
            ModerationNotifier notifier,
            ModerationAudit audit,
            DomainEventPublisher events,
            SanctionHistoryRecorder history,
            SanctionDurationLimit limit,
            SanctionBroadcast broadcast,
            AddressStrictness strictness,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.events = Objects.requireNonNull(events, "events");
        this.history = Objects.requireNonNull(history, "history");
        this.limit = Objects.requireNonNull(limit, "limit");
        this.broadcast = Objects.requireNonNull(broadcast, "broadcast");
        this.strictness = Objects.requireNonNull(strictness, "strictness");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Permanently ban {@code target}, or refuse when the target is exempt. */
    public Result<TempbanState.Active, ModerationError> ban(
            PlayerRef actor, PlayerRef target, Optional<String> reason, boolean silent) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        if (guard.isExempt(target)) {
            audit.tempbanned(actor, target, PERMANENT_LABEL, false, reason);
            notifier.send(actor, ModerationError.TARGET_EXEMPT.messageKey());
            return Result.err(ModerationError.TARGET_EXEMPT);
        }
        return apply(actor, target, reason, silent);
    }

    private Result<TempbanState.Active, ModerationError> apply(
            PlayerRef actor, PlayerRef target, Optional<String> reason, boolean silent) {
        Instant now = clock.instant();
        // A permanent ban is the far-future span; an actor whose tier caps it lands a timed ban instead.
        Duration effective = limit.cap(actor, "ban", PERMANENT_SPAN);
        boolean capped = effective.compareTo(PERMANENT_SPAN) < 0;
        Instant expiry = now.plus(effective);
        TempbanState.Active ban = (TempbanState.Active) TempbanState.active(expiry, Issuer.of(actor), reason, now);
        repository.ensureUserExists(target, now);
        repository.saveTempban(target, ban);
        history.ban(actor, target, reason, capped ? Optional.of(expiry) : Optional.empty());
        kickNow(target, capped, effective, reason);
        events.publish(new PlayerTempbanned(target, ban, now));
        audit.tempbanned(actor, target, capped ? SanctionDuration.format(effective) : PERMANENT_LABEL, true, reason);
        notifier.send(actor, ModerationMessageKey.BAN_APPLIED, Map.of("player", target.name()));
        if (capped) {
            notifier.send(actor, ModerationMessageKey.MOD_DURATION_CAPPED, capLabel(effective));
        }
        fanOutToIps(actor, target, reason, now);
        if (!silent) {
            broadcast.announce(broadcastKey(capped), broadcastPlaceholders(actor, target, reason, capped, effective));
        }
        return Result.ok(ban);
    }

    /**
     * Under STRICT, IP-ban every address the target is known to have connected from so a banned account
     * cannot return on a fresh one from the same connection. Skipped for an exempt target (a defensive second
     * guard so STRICT never collaterally IP-bans an exempt player's shared connection) and fail-safe: an empty
     * IP set leaves the UUID ban as the only effect.
     */
    private void fanOutToIps(PlayerRef actor, PlayerRef target, Optional<String> reason, Instant now) {
        if (!strictness.fansOutToIps() || guard.isExempt(target)) {
            return;
        }
        Set<String> ips = repository.ipHistory(target.uuid());
        if (ips.isEmpty()) {
            return;
        }
        for (String ip : ips) {
            repository.saveIpBan(
                    new IpBan(ip, Optional.empty(), reason, Optional.of(target.uuid()), Issuer.of(actor), now));
            audit.ipBanned(actor, ip, Optional.of(target.uuid()), Optional.empty(), true, reason);
        }
        notifier.send(actor, ModerationMessageKey.MOD_BAN_STRICT_IP, Map.of("count", Integer.toString(ips.size())));
    }

    private void kickNow(PlayerRef target, boolean capped, Duration effective, Optional<String> reason) {
        if (capped) {
            MessageKey key = ModerationMessageKey.TEMPBAN_KICK;
            Map<String, String> ph =
                    Map.of("duration", SanctionDuration.format(effective), "reason", reason.orElse(""));
            sanctions.kick(target, key, notifier.render(target, key, ph));
            return;
        }
        MessageKey key = ModerationMessageKey.BAN_KICK;
        sanctions.kick(target, key, notifier.render(target, key, Map.of("reason", reason.orElse(""))));
    }

    private static MessageKey broadcastKey(boolean capped) {
        return capped ? ModerationMessageKey.MOD_BROADCAST_TEMPBAN : ModerationMessageKey.MOD_BROADCAST_BAN;
    }

    private static Map<String, String> broadcastPlaceholders(
            PlayerRef actor, PlayerRef target, Optional<String> reason, boolean capped, Duration effective) {
        if (capped) {
            return Map.of(
                    "actor", actor.name(),
                    "target", target.name(),
                    "reason", reason.orElse(""),
                    "duration", SanctionDuration.format(effective));
        }
        return Map.of("actor", actor.name(), "target", target.name(), "reason", reason.orElse(""));
    }

    private static Map<String, String> capLabel(Duration effective) {
        return Map.of("cap", SanctionDuration.format(effective));
    }
}
