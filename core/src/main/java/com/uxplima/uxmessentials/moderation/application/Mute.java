package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionBroadcast;
import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerMuted;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * {@code /mute <player> [duration] [reason]} and {@code /tempmute <player> <duration> [reason]}: gag a
 * player's outbound messaging. With no duration the mute is permanent; with one it expires after the parsed
 * span. The exempt target is refused, a malformed duration is refused, and the result is audit-logged either
 * way. On success the mute row is upserted, the target (if online) is told, {@code PlayerMuted} is published,
 * and the messaging context's {@code MutePolicy} starts seeing the new state on its next read.
 *
 * <p>The span is clamped to the actor's {@code mute.maxduration} tier ({@link SanctionDurationLimit}): a timed
 * mute over the cap, or a permanent mute by an actor whose cap is finite, is reduced to the cap and the actor
 * is told {@code MOD_DURATION_CAPPED}. Unless the mute is silent ({@code /mute -s}), the staff broadcast
 * announces it.
 */
public final class Mute {

    private final ModerationRepository repository;
    private final ModerationGuard guard;
    private final Notifier notifier;
    private final ModerationAudit audit;
    private final DomainEventPublisher events;
    private final SanctionHistoryRecorder history;
    private final SanctionDurationLimit limit;
    private final SanctionBroadcast broadcast;
    private final SanctionSync sync;
    private final Clock clock;

    public Mute(
            ModerationRepository repository,
            ModerationGuard guard,
            Notifier notifier,
            ModerationAudit audit,
            DomainEventPublisher events,
            SanctionHistoryRecorder history,
            SanctionDurationLimit limit,
            SanctionBroadcast broadcast,
            SanctionSync sync,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.events = Objects.requireNonNull(events, "events");
        this.history = Objects.requireNonNull(history, "history");
        this.limit = Objects.requireNonNull(limit, "limit");
        this.broadcast = Objects.requireNonNull(broadcast, "broadcast");
        this.sync = Objects.requireNonNull(sync, "sync");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Mute {@code target}: permanent when {@code rawDuration} is blank, timed otherwise. */
    public Result<MuteState, ModerationError> mute(
            PlayerRef actor, PlayerRef target, String rawDuration, Optional<String> reason, boolean silent) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        if (guard.isExempt(target)) {
            return reject(actor, target, rawDuration, reason, ModerationError.TARGET_EXEMPT);
        }
        SanctionDuration.Parsed parsed = SanctionDuration.parse(rawDuration);
        if (parsed.malformed()) {
            return reject(actor, target, rawDuration, reason, ModerationError.BAD_DURATION);
        }
        return apply(actor, target, parsed, reason, silent);
    }

    private Result<MuteState, ModerationError> apply(
            PlayerRef actor,
            PlayerRef target,
            SanctionDuration.Parsed parsed,
            Optional<String> reason,
            boolean silent) {
        Instant now = clock.instant();
        Issuer issuer = Issuer.of(actor);
        // A permanent mute by a capped actor becomes the far-future span clamped to the cap, i.e. a timed mute.
        Duration requested = parsed.duration().orElse(Ban.PERMANENT_SPAN);
        Duration span = limit.cap(actor, "mute", requested);
        boolean capped = span.compareTo(requested) < 0;
        boolean timed = parsed.duration().isPresent() || capped;
        MuteState mute =
                timed ? MuteState.timed(now.plus(span), issuer, reason, now) : MuteState.permanent(issuer, reason, now);
        repository.ensureUserExists(target, now);
        repository.saveMute(target, mute);
        Optional<Duration> effective = timed ? Optional.of(span) : Optional.empty();
        history.mute(actor, target, reason, effective.map(now::plus));
        notifyTarget(target, effective, reason);
        // A peer re-reads the mute from the shared DB on the next chat attempt; this frame is published so a
        // future mute-caching peer has an invalidation hook (a no-op today and with no bus).
        sync.muteChanged(target);
        events.publish(new PlayerMuted(target, mute, now));
        audit.muted(actor, target, effective.map(SanctionDuration::format), true, reason);
        if (capped) {
            notifier.send(
                    actor, ModerationMessageKey.MOD_DURATION_CAPPED, Map.of("cap", SanctionDuration.format(span)));
        }
        if (!silent) {
            announce(actor, target, reason, effective);
        }
        return Result.ok(mute);
    }

    private void announce(PlayerRef actor, PlayerRef target, Optional<String> reason, Optional<Duration> effective) {
        broadcast.announce(
                ModerationMessageKey.MOD_BROADCAST_MUTE,
                Map.of(
                        "actor", actor.name(),
                        "target", target.name(),
                        "reason", reason.orElse(""),
                        "duration", effective.map(SanctionDuration::format).orElse("permanent")));
    }

    private void notifyTarget(PlayerRef target, Optional<Duration> effective, Optional<String> reason) {
        Map<String, String> ph = Map.of(
                "duration", effective.map(SanctionDuration::format).orElse("permanent"),
                "reason", reason.orElse(""));
        notifier.send(target, ModerationMessageKey.MUTE_NOTIFY_TARGET, ph);
    }

    private Result<MuteState, ModerationError> reject(
            PlayerRef actor, PlayerRef target, String rawDuration, Optional<String> reason, ModerationError error) {
        Optional<String> label = rawDuration.isBlank() ? Optional.empty() : Optional.of(rawDuration);
        audit.muted(actor, target, label, false, reason);
        notifier.send(actor, error.messageKey());
        return Result.err(error);
    }
}
