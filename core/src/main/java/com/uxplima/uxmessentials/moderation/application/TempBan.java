package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionBroadcast;
import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.moderation.domain.DrawnReason;
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
 * row is stored ({@link GuardedWrite}, so a ban landing at the same moment elsewhere is never overwritten
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
            return reject(actor, actor, target, rawDuration, reason, ModerationError.TARGET_EXEMPT);
        }
        Optional<Duration> requested = SanctionDuration.parse(rawDuration).duration();
        if (requested.isEmpty()) {
            // A tempban must be timed: a permanent or malformed input is BAD_DURATION (no permanent tempban).
            return reject(actor, actor, target, rawDuration, reason, ModerationError.BAD_DURATION);
        }
        if (tooLong(requested.get())) {
            return rejectTooLong(actor, actor, target, rawDuration, reason);
        }
        Instant now = clock.instant();
        Duration span = limit.cap(actor, "ban", requested.get());
        TempbanState.Active ban = proposal(actor, span, reason, Optional.empty(), now);
        Predicate<TempbanState.Active> longer =
                GuardedWrite.outlasting(SanctionStrength.endOf(ban), SanctionStrength::endOf);
        return switch (GuardedWrite.storeBan(repository, target, ban, now, longer)) {
            case GuardedWrite.Kept<TempbanState.Active> kept -> {
                audit.tempbanned(actor, target, SanctionDuration.format(span), false, SanctionStrength.HELD);
                // A capped request may have been longer than the running ban; without this line the refusal would
                // read as if the staff member had asked for a shorter one.
                if (span.compareTo(requested.get()) < 0) {
                    tellCapped(actor, span);
                }
                GuardedWrite.tellBanKept(notifier, actor, target, kept.running(), now);
                yield Result.err(ModerationError.LONGER_BAN_RUNNING);
            }
            case GuardedWrite.Stored<TempbanState.Active> stored -> {
                announce(actor, actor, target, ban, span, span.compareTo(requested.get()) < 0, silent, now);
                yield Result.ok(ban);
            }
        };
    }

    /**
     * Leave {@code target} banned for at least the parsed span: the warn escalation's way in. A ban already in
     * force that ends sooner is replaced by this one as a fresh tempban (stored, recorded, announced and synced
     * like any tempban); one that ends at the same instant or later, a permanent ban above all, is kept and the
     * rung is audited as held, so a rung can never shorten a ban. The ban is issued under {@code actor}, the server
     * rule, while what the use case says about it goes to {@code told}, the staff member whose warning set it off. A
     * {@code drawn} reason is the catalog line {@code reason} was drawn from for the target, stored with the ban so
     * every reader reads it in their own language.
     */
    Result<EscalationOutcome, ModerationError> escalate(
            PlayerRef actor,
            PlayerRef told,
            PlayerRef target,
            String rawDuration,
            Optional<String> reason,
            Optional<DrawnReason> drawn,
            boolean silent) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(told, "told");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(rawDuration, "rawDuration");
        Objects.requireNonNull(reason, "reason");
        if (guard.isExempt(target)) {
            return reject(actor, told, target, rawDuration, reason, ModerationError.TARGET_EXEMPT);
        }
        Optional<Duration> requested = SanctionDuration.parse(rawDuration).duration();
        if (requested.isEmpty()) {
            return reject(actor, told, target, rawDuration, reason, ModerationError.BAD_DURATION);
        }
        if (tooLong(requested.get())) {
            return rejectTooLong(actor, told, target, rawDuration, reason);
        }
        Instant now = clock.instant();
        Duration span = limit.cap(actor, "ban", requested.get());
        TempbanState.Active ban = proposal(actor, span, reason, drawn, now);
        Predicate<TempbanState.Active> atLeastAsLong =
                GuardedWrite.notOutlastedBy(SanctionStrength.endOf(ban), SanctionStrength::endOf);
        return switch (GuardedWrite.storeBan(repository, target, ban, now, atLeastAsLong)) {
            case GuardedWrite.Kept<TempbanState.Active> kept -> {
                audit.tempbanned(actor, target, SanctionDuration.format(span), false, SanctionStrength.HELD);
                yield Result.ok(EscalationOutcome.ALREADY_STRONGER);
            }
            case GuardedWrite.Stored<TempbanState.Active> stored -> {
                announce(actor, told, target, ban, span, span.compareTo(requested.get()) < 0, silent, now);
                yield Result.ok(stored.replacedRunning() ? EscalationOutcome.EXTENDED : EscalationOutcome.APPLIED);
            }
        };
    }

    private static TempbanState.Active proposal(
            PlayerRef actor, Duration span, Optional<String> reason, Optional<DrawnReason> drawn, Instant now) {
        return (TempbanState.Active) TempbanState.active(now.plus(span), Issuer.of(actor), reason, now, drawn);
    }

    /** Everything a stored tempban sets off: the history row, the kick, the sync, the event and every notice. */
    private void announce(
            PlayerRef actor,
            PlayerRef told,
            PlayerRef target,
            TempbanState.Active ban,
            Duration span,
            boolean capped,
            boolean silent,
            Instant now) {
        Optional<String> reason = ban.reason();
        history.ban(actor, target, reason, ban.drawnReason(), Optional.of(ban.until()), now);
        BanScreen.of(ban, now).kick(sanctions, notifier, target);
        // The local kick removed the target here; this wakes peers that have them online (a no-op with no bus).
        sync.banChanged(target);
        events.publish(new PlayerTempbanned(target, ban, now));
        audit.tempbanned(actor, target, SanctionDuration.format(span), true, reason);
        if (capped) {
            tellCapped(told, span);
        }
        // The answer to the person who ran the command, separate from the staff channel and never suppressed
        // with it: a silent tempban hides the sanction from the channel, not from the operator.
        notifier.send(
                told,
                ModerationMessageKey.TEMPBAN_APPLIED,
                Map.of("player", target.name(), "duration", SanctionDuration.format(span)));
        if (!silent) {
            broadcast.announce(
                    ModerationMessageKey.MOD_BROADCAST_TEMPBAN,
                    Map.of(
                            "actor", actor.name(),
                            "target", target.name(),
                            "reason", reason.orElse(""),
                            "duration", SanctionDuration.format(span)),
                    ban.drawnReason());
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
            PlayerRef actor, PlayerRef told, PlayerRef target, String rawDuration, Optional<String> reason) {
        audit.tempbanned(actor, target, rawDuration, false, reason);
        notifier.send(told, ModerationMessageKey.TEMPBAN_TOO_LONG);
        return Result.err(ModerationError.BAD_DURATION);
    }

    private <T> Result<T, ModerationError> reject(
            PlayerRef actor,
            PlayerRef told,
            PlayerRef target,
            String rawDuration,
            Optional<String> reason,
            ModerationError error) {
        Optional<String> label = rawDuration.isBlank() ? Optional.empty() : Optional.of(rawDuration);
        audit.tempbanned(actor, target, label.orElse(""), false, reason);
        notifier.send(told, error.messageKey());
        return Result.err(error);
    }
}
