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
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerTempbanned;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * {@code /tempban <player> <duration> [reason]}: ban a player until a wall-clock expiry. Unlike a mute or
 * jail there is no permanent form (a permanent ban is an {@code /banip} or the external ban-list) so a
 * blank/malformed/zero duration is refused. The exempt target is refused. On success the tempban row is
 * upserted, an online target is kicked immediately via {@link Sanctions}, {@code PlayerTempbanned} is
 * published, and the ban-on-login listener bars their reconnection until the expiry passes.
 *
 * <p>The requested span is first clamped to the actor's {@code ban.maxduration} tier ({@link
 * SanctionDurationLimit}); when it is reduced the actor is told {@code MOD_DURATION_CAPPED}. Unless the ban is
 * silent ({@code /tempban -s}), the staff broadcast announces it.
 */
public final class TempBan {

    private final ModerationRepository repository;
    private final Sanctions sanctions;
    private final ModerationGuard guard;
    private final Notifier notifier;
    private final ModerationAudit audit;
    private final DomainEventPublisher events;
    private final SanctionHistoryRecorder history;
    private final SanctionDurationLimit limit;
    private final SanctionBroadcast broadcast;
    private final SanctionSync sync;
    private final Clock clock;

    public TempBan(
            ModerationRepository repository,
            Sanctions sanctions,
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
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
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

    /** Tempban {@code target} for the parsed span of {@code rawDuration}. */
    public Result<TempbanState.Active, ModerationError> tempban(
            PlayerRef actor, PlayerRef target, String rawDuration, Optional<String> reason, boolean silent) {
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
        return apply(actor, target, span.get(), reason, silent, clock.instant());
    }

    /**
     * Leave {@code target} banned for at least the parsed span: the warn escalation's way in. A ban already in
     * force that ends sooner is replaced by this one as a fresh tempban (stored, recorded, announced and synced
     * like any tempban); one that lasts as long or longer, a permanent ban above all, is kept and the rung is
     * audited as not applied, so a rung can never shorten a ban. Only the caller is told the outcome, since the
     * actor here is the server rule, not a person.
     */
    public Result<EscalationOutcome, ModerationError> escalate(
            PlayerRef actor, PlayerRef target, String rawDuration, Optional<String> reason, boolean silent) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(rawDuration, "rawDuration");
        Objects.requireNonNull(reason, "reason");
        if (guard.isExempt(target)) {
            return reject(actor, target, rawDuration, reason, ModerationError.TARGET_EXEMPT);
        }
        Optional<Duration> requested = SanctionDuration.parse(rawDuration).duration();
        if (requested.isEmpty()) {
            return reject(actor, target, rawDuration, reason, ModerationError.BAD_DURATION);
        }
        Instant now = clock.instant();
        Duration span = limit.cap(actor, "ban", requested.get());
        if (repository.loadTempban(target) instanceof TempbanState.Active running && running.isActiveAt(now)) {
            if (!SanctionStrength.outlasts(Optional.of(now.plus(span)), SanctionStrength.endOf(running))) {
                audit.tempbanned(actor, target, SanctionDuration.format(span), false, reason);
                return Result.ok(EscalationOutcome.ALREADY_STRONGER);
            }
            return apply(actor, target, requested.get(), reason, silent, now).map(ban -> EscalationOutcome.EXTENDED);
        }
        return apply(actor, target, requested.get(), reason, silent, now).map(ban -> EscalationOutcome.APPLIED);
    }

    private Result<TempbanState.Active, ModerationError> apply(
            PlayerRef actor,
            PlayerRef target,
            Duration requested,
            Optional<String> reason,
            boolean silent,
            Instant now) {
        Duration span = limit.cap(actor, "ban", requested);
        boolean capped = span.compareTo(requested) < 0;
        TempbanState.Active ban =
                (TempbanState.Active) TempbanState.active(now.plus(span), Issuer.of(actor), reason, now);
        repository.ensureUserExists(target, now);
        repository.saveTempban(target, ban);
        history.ban(actor, target, reason, Optional.of(now.plus(span)));
        kickNow(target, span, reason);
        // The local kick removed the target here; this wakes peers that have them online (a no-op with no bus).
        sync.banChanged(target);
        events.publish(new PlayerTempbanned(target, ban, now));
        audit.tempbanned(actor, target, SanctionDuration.format(span), true, reason);
        if (capped) {
            notifier.send(
                    actor, ModerationMessageKey.MOD_DURATION_CAPPED, Map.of("cap", SanctionDuration.format(span)));
        }
        // The answer to the person who ran the command, separate from the staff channel and never suppressed
        // with it: a silent tempban hides the sanction from the channel, not from the operator.
        notifier.send(
                actor,
                ModerationMessageKey.TEMPBAN_APPLIED,
                Map.of("player", target.name(), "duration", SanctionDuration.format(span)));
        if (!silent) {
            broadcast.announce(
                    ModerationMessageKey.MOD_BROADCAST_TEMPBAN,
                    Map.of(
                            "actor", actor.name(),
                            "target", target.name(),
                            "reason", reason.orElse(""),
                            "duration", SanctionDuration.format(span)));
        }
        return Result.ok(ban);
    }

    private void kickNow(PlayerRef target, Duration span, Optional<String> reason) {
        Map<String, String> ph = Map.of("duration", SanctionDuration.format(span), "reason", reason.orElse(""));
        MessageKey key = ModerationMessageKey.TEMPBAN_KICK;
        sanctions.kick(target, key, notifier.render(target, key, ph));
    }

    private <T> Result<T, ModerationError> reject(
            PlayerRef actor, PlayerRef target, String rawDuration, Optional<String> reason, ModerationError error) {
        Optional<String> label = rawDuration.isBlank() ? Optional.empty() : Optional.of(rawDuration);
        audit.tempbanned(actor, target, label.orElse(""), false, reason);
        notifier.send(actor, error.messageKey());
        return Result.err(error);
    }
}
