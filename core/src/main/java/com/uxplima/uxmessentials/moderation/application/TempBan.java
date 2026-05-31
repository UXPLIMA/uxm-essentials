package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
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
 * {@code /tempban <player> <duration> [reason]}: ban a player until a wall-clock expiry. Unlike a mute or
 * jail there is no permanent form — a permanent ban is an {@code /banip} or the external ban-list — so a
 * blank/malformed/zero duration is refused. The exempt target is refused. On success the tempban row is
 * upserted, an online target is kicked immediately via {@link Sanctions}, {@code PlayerTempbanned} is
 * published, and the ban-on-login listener bars their reconnection until the expiry passes.
 */
public final class TempBan {

    private final ModerationRepository repository;
    private final Sanctions sanctions;
    private final ModerationGuard guard;
    private final ModerationNotifier notifier;
    private final ModerationAudit audit;
    private final DomainEventPublisher events;
    private final Clock clock;

    public TempBan(
            ModerationRepository repository,
            Sanctions sanctions,
            ModerationGuard guard,
            ModerationNotifier notifier,
            ModerationAudit audit,
            DomainEventPublisher events,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Tempban {@code target} for the parsed span of {@code rawDuration}. */
    public Result<TempbanState.Active, ModerationError> tempban(
            PlayerRef actor, PlayerRef target, String rawDuration, Optional<String> reason) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(rawDuration, "rawDuration");
        Objects.requireNonNull(reason, "reason");
        if (guard.isExempt(target)) {
            return reject(actor, target, rawDuration, reason, ModerationError.TARGET_EXEMPT);
        }
        Optional<Duration> span = SanctionDuration.parse(rawDuration).duration();
        if (span.isEmpty()) {
            // A tempban must be timed: a permanent or malformed input is BAD_DURATION (no permanent tempban).
            return reject(actor, target, rawDuration, reason, ModerationError.BAD_DURATION);
        }
        return apply(actor, target, span.get(), reason);
    }

    private Result<TempbanState.Active, ModerationError> apply(
            PlayerRef actor, PlayerRef target, Duration span, Optional<String> reason) {
        Instant now = clock.instant();
        TempbanState.Active ban =
                (TempbanState.Active) TempbanState.active(now.plus(span), Issuer.of(actor), reason, now);
        repository.ensureUserExists(target, now);
        repository.saveTempban(target, ban);
        kickNow(target, span, reason);
        events.publish(new PlayerTempbanned(target, ban, now));
        audit.tempbanned(actor, target, SanctionDuration.format(span), true, reason);
        return Result.ok(ban);
    }

    private void kickNow(PlayerRef target, Duration span, Optional<String> reason) {
        Map<String, String> ph = Map.of("duration", SanctionDuration.format(span), "reason", reason.orElse(""));
        MessageKey key = ModerationMessageKey.TEMPBAN_KICK;
        sanctions.kick(target, key, notifier.render(target, key, ph));
    }

    private Result<TempbanState.Active, ModerationError> reject(
            PlayerRef actor, PlayerRef target, String rawDuration, Optional<String> reason, ModerationError error) {
        Optional<String> label = rawDuration.isBlank() ? Optional.empty() : Optional.of(rawDuration);
        audit.tempbanned(actor, target, label.orElse(""), false, reason);
        notifier.send(actor, error.messageKey());
        return Result.err(error);
    }
}
