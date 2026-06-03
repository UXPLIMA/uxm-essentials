package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerMuted;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * {@code /mute <player> [duration] [reason]} and {@code /tempmute <player> <duration> [reason]}: gag a
 * player's outbound messaging. With no duration the mute is permanent; with one it expires after the parsed
 * span. The exempt target is refused, a malformed duration is refused, and the result is audit-logged either
 * way. On success the mute row is upserted, the target (if online) is told, {@code PlayerMuted} is published,
 * and the messaging context's {@code MutePolicy} starts seeing the new state on its next read.
 */
public final class Mute {

    private final ModerationRepository repository;
    private final ModerationGuard guard;
    private final ModerationNotifier notifier;
    private final ModerationAudit audit;
    private final DomainEventPublisher events;
    private final SanctionHistoryRecorder history;
    private final Clock clock;

    public Mute(
            ModerationRepository repository,
            ModerationGuard guard,
            ModerationNotifier notifier,
            ModerationAudit audit,
            DomainEventPublisher events,
            SanctionHistoryRecorder history,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.events = Objects.requireNonNull(events, "events");
        this.history = Objects.requireNonNull(history, "history");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Mute {@code target}: permanent when {@code rawDuration} is blank, timed otherwise. */
    public Result<MuteState, ModerationError> mute(
            PlayerRef actor, PlayerRef target, String rawDuration, Optional<String> reason) {
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
        return apply(actor, target, parsed, reason);
    }

    private Result<MuteState, ModerationError> apply(
            PlayerRef actor, PlayerRef target, SanctionDuration.Parsed parsed, Optional<String> reason) {
        Instant now = clock.instant();
        Issuer issuer = Issuer.of(actor);
        MuteState mute = parsed.duration()
                .map(d -> MuteState.timed(now.plus(d), issuer, reason, now))
                .orElseGet(() -> MuteState.permanent(issuer, reason, now));
        repository.ensureUserExists(target, now);
        repository.saveMute(target, mute);
        history.mute(actor, target, reason, parsed.duration().map(now::plus));
        notifyTarget(target, parsed, reason);
        events.publish(new PlayerMuted(target, mute, now));
        Optional<String> label = parsed.duration().map(SanctionDuration::format);
        audit.muted(actor, target, label, true, reason);
        return Result.ok(mute);
    }

    private void notifyTarget(PlayerRef target, SanctionDuration.Parsed parsed, Optional<String> reason) {
        Map<String, String> ph = Map.of(
                "duration", parsed.duration().map(SanctionDuration::format).orElse("permanent"),
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
