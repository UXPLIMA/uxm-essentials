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
    private final Clock clock;

    public Ban(
            ModerationRepository repository,
            Sanctions sanctions,
            ModerationGuard guard,
            ModerationNotifier notifier,
            ModerationAudit audit,
            DomainEventPublisher events,
            SanctionHistoryRecorder history,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.events = Objects.requireNonNull(events, "events");
        this.history = Objects.requireNonNull(history, "history");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Permanently ban {@code target}, or refuse when the target is exempt. */
    public Result<TempbanState.Active, ModerationError> ban(
            PlayerRef actor, PlayerRef target, Optional<String> reason) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        if (guard.isExempt(target)) {
            audit.tempbanned(actor, target, PERMANENT_LABEL, false, reason);
            notifier.send(actor, ModerationError.TARGET_EXEMPT.messageKey());
            return Result.err(ModerationError.TARGET_EXEMPT);
        }
        return apply(actor, target, reason);
    }

    private Result<TempbanState.Active, ModerationError> apply(
            PlayerRef actor, PlayerRef target, Optional<String> reason) {
        Instant now = clock.instant();
        TempbanState.Active ban =
                (TempbanState.Active) TempbanState.active(now.plus(PERMANENT_SPAN), Issuer.of(actor), reason, now);
        repository.ensureUserExists(target, now);
        repository.saveTempban(target, ban);
        history.ban(actor, target, reason, Optional.empty());
        kickNow(target, reason);
        events.publish(new PlayerTempbanned(target, ban, now));
        audit.tempbanned(actor, target, PERMANENT_LABEL, true, reason);
        notifier.send(actor, ModerationMessageKey.BAN_APPLIED, Map.of("player", target.name()));
        return Result.ok(ban);
    }

    private void kickNow(PlayerRef target, Optional<String> reason) {
        MessageKey key = ModerationMessageKey.BAN_KICK;
        String rendered = notifier.render(target, key, Map.of("reason", reason.orElse("")));
        sanctions.kick(target, key, rendered);
    }
}
