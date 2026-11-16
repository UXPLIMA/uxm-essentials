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
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerTempbanned;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * {@code /tempban <player> <duration> [reason]}: ban a player until a wall-clock expiry. Unlike a mute or
 * jail there is no permanent form (a permanent ban is {@code /ban}) so a blank/malformed/zero duration is refused, and
 * so is one as long as the far-future span a permanent ban is stored with ({@link Ban#PERMANENT_SPAN}).
 * The exempt target is refused, and so is a target whose running ban would outlast this one. On success the ban
 * row is stored ({@link GuardedBanWrite}, so a ban landing at the same moment elsewhere is never overwritten
 * blindly), an online target is kicked immediately via {@link Sanctions}, {@code PlayerTempbanned} is
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

    /**
     * Tempban {@code target} for the parsed span of {@code rawDuration}. A ban already in force that would outlast
     * this one is never cut short: the request is refused with {@link ModerationError#LONGER_BAN_RUNNING} and the
     * actor is told which ban is running, so shortening a ban takes an explicit {@code /unban} first.
     */
    public Result<TempbanState.Active, ModerationError> tempban(
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
            // A tempban must be timed: a permanent or malformed input is BAD_DURATION (no permanent tempban).
            return reject(actor, target, rawDuration, reason, ModerationError.BAD_DURATION);
        }
        if (tooLong(requested.get())) {
            return rejectTooLong(actor, target, rawDuration, reason);
        }
        Instant now = clock.instant();
        Duration span = limit.cap(actor, "ban", requested.get());
        TempbanState.Active ban = proposal(actor, span, reason, now);
        return switch (GuardedBanWrite.store(repository, target, ban, now, GuardedBanWrite.outlasting(ban))) {
            case GuardedBanWrite.Kept kept -> {
                audit.tempbanned(actor, target, SanctionDuration.format(span), false, SanctionStrength.HELD);
                // A capped request may have been longer than the running ban; without this line the refusal would
                // read as if the staff member had asked for a shorter one.
                if (span.compareTo(requested.get()) < 0) {
                    tellCapped(actor, span);
                }
                GuardedBanWrite.tellKept(notifier, actor, target, kept.running(), now);
                yield Result.err(ModerationError.LONGER_BAN_RUNNING);
            }
            case GuardedBanWrite.Stored stored -> {
                announce(actor, target, ban, span, span.compareTo(requested.get()) < 0, reason, silent, now);
                yield Result.ok(ban);
            }
        };
    }

    /**
     * Leave {@code target} banned for at least the parsed span: the warn escalation's way in. A ban already in
     * force that ends sooner is replaced by this one as a fresh tempban (stored, recorded, announced and synced
     * like any tempban); one that ends at the same instant or later, a permanent ban above all, is kept and the
     * rung is audited as held, so a rung can never shorten a ban. Only the caller is told the outcome, since the
     * actor here is the server rule, not a person.
     */
    Result<EscalationOutcome, ModerationError> escalate(
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
        if (tooLong(requested.get())) {
            return rejectTooLong(actor, target, rawDuration, reason);
        }
        Instant now = clock.instant();
        Duration span = limit.cap(actor, "ban", requested.get());
        TempbanState.Active ban = proposal(actor, span, reason, now);
        return switch (GuardedBanWrite.store(repository, target, ban, now, GuardedBanWrite.notOutlastedBy(ban))) {
            case GuardedBanWrite.Kept kept -> {
                audit.tempbanned(actor, target, SanctionDuration.format(span), false, SanctionStrength.HELD);
                yield Result.ok(EscalationOutcome.ALREADY_STRONGER);
            }
            case GuardedBanWrite.Stored stored -> {
                announce(actor, target, ban, span, span.compareTo(requested.get()) < 0, reason, silent, now);
                yield Result.ok(stored.replacedRunning() ? EscalationOutcome.EXTENDED : EscalationOutcome.APPLIED);
            }
        };
    }

    private static TempbanState.Active proposal(PlayerRef actor, Duration span, Optional<String> reason, Instant now) {
        return (TempbanState.Active) TempbanState.active(now.plus(span), Issuer.of(actor), reason, now);
    }

    /** Everything a stored tempban sets off: the history row, the kick, the sync, the event and every notice. */
    private void announce(
            PlayerRef actor,
            PlayerRef target,
            TempbanState.Active ban,
            Duration span,
            boolean capped,
            Optional<String> reason,
            boolean silent,
            Instant now) {
        history.ban(actor, target, reason, Optional.of(ban.until()));
        BanScreen.of(ban, now).kick(sanctions, notifier, target);
        // The local kick removed the target here; this wakes peers that have them online (a no-op with no bus).
        sync.banChanged(target);
        events.publish(new PlayerTempbanned(target, ban, now));
        audit.tempbanned(actor, target, SanctionDuration.format(span), true, reason);
        if (capped) {
            tellCapped(actor, span);
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
    }

    private void tellCapped(PlayerRef actor, Duration span) {
        notifier.send(actor, ModerationMessageKey.MOD_DURATION_CAPPED, Map.of("cap", SanctionDuration.format(span)));
    }

    /**
     * True for a span that reaches the far-future stand-in a permanent ban is stored with: stored, it would read as a
     * permanent ban everywhere it is shown, while its own messages, history and audit called it a tempban.
     */
    private static boolean tooLong(Duration span) {
        return span.compareTo(Ban.PERMANENT_SPAN) >= 0;
    }

    /** Refuse a span too long for a tempban, pointing the actor at {@code /ban}. */
    private <T> Result<T, ModerationError> rejectTooLong(
            PlayerRef actor, PlayerRef target, String rawDuration, Optional<String> reason) {
        audit.tempbanned(actor, target, rawDuration, false, reason);
        notifier.send(actor, ModerationMessageKey.TEMPBAN_TOO_LONG);
        return Result.err(ModerationError.BAD_DURATION);
    }

    private <T> Result<T, ModerationError> reject(
            PlayerRef actor, PlayerRef target, String rawDuration, Optional<String> reason, ModerationError error) {
        Optional<String> label = rawDuration.isBlank() ? Optional.empty() : Optional.of(rawDuration);
        audit.tempbanned(actor, target, label.orElse(""), false, reason);
        notifier.send(actor, error.messageKey());
        return Result.err(error);
    }
}
