package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionBroadcast;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerWarned;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * {@code /warn <player> [reason]}: append a warning to a player's history. The exempt target is refused
 * (audit-logged). Otherwise the warning is appended to the append-only history (never overwritten), the
 * target (if online) is told, {@code PlayerWarned} is published with the new total, and the action is
 * audit-logged.
 *
 * <p>After the warning is recorded its new total is run through the {@link WarnEscalator}: when a configured
 * rung names that exact count, the matching sanction (mute/tempban/kick) is applied automatically and the
 * actor is told. Unless the warn is silent ({@code /warn -s}) the staff broadcast announces it; a silent warn
 * also escalates silently.
 */
public final class IssueWarn {

    private final ModerationRepository repository;
    private final ModerationGuard guard;
    private final Notifier notifier;
    private final ModerationAudit audit;
    private final DomainEventPublisher events;
    private final SanctionHistoryRecorder history;
    private final SanctionBroadcast broadcast;
    private final WarnEscalator escalator;
    private final Clock clock;

    public IssueWarn(
            ModerationRepository repository,
            ModerationGuard guard,
            Notifier notifier,
            ModerationAudit audit,
            DomainEventPublisher events,
            SanctionHistoryRecorder history,
            SanctionBroadcast broadcast,
            WarnEscalator escalator,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.events = Objects.requireNonNull(events, "events");
        this.history = Objects.requireNonNull(history, "history");
        this.broadcast = Objects.requireNonNull(broadcast, "broadcast");
        this.escalator = Objects.requireNonNull(escalator, "escalator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Warn {@code target} with an optional reason, returning the appended warning. */
    public Result<PlayerWarned, ModerationError> warn(
            PlayerRef actor, PlayerRef target, Optional<String> reason, boolean silent) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        if (guard.isExempt(target)) {
            audit.warned(actor, target, false, reason);
            notifier.send(actor, ModerationError.TARGET_EXEMPT.messageKey());
            return Result.err(ModerationError.TARGET_EXEMPT);
        }
        Instant now = clock.instant();
        Warn warn = Warn.standing(Issuer.of(actor), reason, now);
        repository.ensureUserExists(target, now);
        int total = repository.appendWarn(target, warn);
        PlayerWarned event = new PlayerWarned(target, warn, total);
        history.warn(actor, target, reason, Optional.empty());
        notifier.send(target, ModerationMessageKey.WARN_NOTIFY_TARGET, Map.of("reason", reason.orElse("")));
        events.publish(event);
        audit.warned(actor, target, true, reason);
        notifier.send(actor, ModerationMessageKey.WARN_APPLIED, applied(target, total));
        if (!silent) {
            broadcast.announce(
                    ModerationMessageKey.MOD_BROADCAST_WARN,
                    Map.of("actor", actor.name(), "target", target.name(), "reason", reason.orElse("")));
        }
        escalator.escalate(actor, target, total, silent);
        return Result.ok(event);
    }

    private static Map<String, String> applied(PlayerRef target, int total) {
        return Map.of("player", target.name(), "count", Integer.toString(total));
    }
}
