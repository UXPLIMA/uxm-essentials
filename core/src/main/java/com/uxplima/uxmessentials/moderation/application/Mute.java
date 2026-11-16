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
import com.uxplima.uxmessentials.moderation.domain.DrawnReason;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
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
 * way. A mute already in force is refused, never replaced. On success the mute row is stored by compare and set,
 * the target (if online) is told, {@code PlayerMuted} is published, and the messaging context's {@code MutePolicy}
 * starts seeing the new state on its next read.
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

    /**
     * Mute {@code target}: permanent when {@code rawDuration} is blank, timed otherwise. The rule bans follow: a mute
     * that ends later than the running one, or a second permanent one, replaces it; one that would end sooner is
     * refused with {@link ModerationError#ALREADY_MUTED} and the actor is told which mute runs and for how long, so
     * shortening a mute takes an explicit {@code /unmute} first. The write is compare and set, so a mute that lands
     * elsewhere between the read and the write is judged by the same rule instead of being overwritten.
     */
    public Result<MuteState, ModerationError> mute(
            PlayerRef actor, PlayerRef target, String rawDuration, Optional<String> reason, boolean silent) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(reason, "reason");
        if (guard.isExempt(target)) {
            return reject(actor, actor, target, rawDuration, reason, ModerationError.TARGET_EXEMPT);
        }
        SanctionDuration.Parsed parsed = SanctionDuration.parse(rawDuration);
        if (parsed.malformed()) {
            return reject(actor, actor, target, rawDuration, reason, ModerationError.BAD_DURATION);
        }
        Instant now = clock.instant();
        Plan plan = plan(actor, parsed);
        MuteState mute = proposal(actor, plan, reason, Optional.empty(), now);
        Predicate<MuteState> longer = GuardedWrite.outlasting(mute.expiry(), MuteState::expiry);
        return switch (GuardedWrite.storeMute(repository, target, mute, now, longer)) {
            case GuardedWrite.Kept<MuteState> kept -> {
                audit.muted(actor, target, label(rawDuration), false, reason);
                GuardedWrite.tellKept(
                        notifier,
                        actor,
                        kept.running().expiry(),
                        now,
                        ModerationMessageKey.MUTE_ALREADY_PERMANENT,
                        ModerationMessageKey.MUTE_ALREADY_TIMED,
                        Map.of("player", target.name()));
                yield Result.err(ModerationError.ALREADY_MUTED);
            }
            case GuardedWrite.Stored<MuteState> stored -> {
                announce(actor, actor, target, mute, plan, reason, Optional.empty(), silent, now);
                yield Result.ok(mute);
            }
        };
    }

    /**
     * {@code /tempmute}: {@link #mute} with a duration that must be a real span. A permanent ({@code perm}) or
     * malformed duration is refused with {@link ModerationError#BAD_DURATION}, the way {@code /tempban} refuses
     * one, so a command named for a timed mute never leaves a permanent one. An exempt target is still refused as
     * exempt first, as {@code /tempban} does.
     */
    public Result<MuteState, ModerationError> tempmute(
            PlayerRef actor, PlayerRef target, String rawDuration, Optional<String> reason, boolean silent) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(rawDuration, "rawDuration");
        Objects.requireNonNull(reason, "reason");
        if (!guard.isExempt(target)
                && SanctionDuration.parse(rawDuration).duration().isEmpty()) {
            return reject(actor, actor, target, rawDuration, reason, ModerationError.BAD_DURATION);
        }
        return mute(actor, target, rawDuration, reason, silent);
    }

    /**
     * Leave {@code target} muted for at least the given span (permanent when blank): the warn escalation's way
     * in. Unlike {@link #mute}, a mute already in force is no refusal here, because a ladder rung fires on
     * every warning that reaches it and the earlier rung's mute is usually still running. A rung that lasts
     * longer replaces the running mute as a fresh one (stored, recorded, announced and synced like any mute);
     * a running mute that ends at the same instant or later, or a permanent one, is kept and the rung is
     * audited as held. The write is compare and set, so a staff mute landing while the rung is written is kept
     * by the same rule. The mute is issued under {@code actor}, the server rule, while what the use case says
     * about it goes to {@code told}, the staff member whose warning set it off. A {@code drawn} reason is the catalog
     * line {@code reason} was drawn from for the target, stored with the mute so every reader reads it in their own
     * language.
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
        Objects.requireNonNull(reason, "reason");
        if (guard.isExempt(target)) {
            return reject(actor, told, target, rawDuration, reason, ModerationError.TARGET_EXEMPT);
        }
        SanctionDuration.Parsed parsed = SanctionDuration.parse(rawDuration);
        if (parsed.malformed()) {
            return reject(actor, told, target, rawDuration, reason, ModerationError.BAD_DURATION);
        }
        Instant now = clock.instant();
        Plan plan = plan(actor, parsed);
        MuteState mute = proposal(actor, plan, reason, drawn, now);
        Predicate<MuteState> atLeastAsLong = GuardedWrite.notOutlastedBy(mute.expiry(), MuteState::expiry);
        return switch (GuardedWrite.storeMute(repository, target, mute, now, atLeastAsLong)) {
            case GuardedWrite.Kept<MuteState> kept -> {
                audit.muted(
                        actor, target, plan.effective().map(SanctionDuration::format), false, SanctionStrength.HELD);
                yield Result.ok(EscalationOutcome.ALREADY_STRONGER);
            }
            case GuardedWrite.Stored<MuteState> stored -> {
                announce(actor, told, target, mute, plan, reason, drawn, silent, now);
                yield Result.ok(stored.replacedRunning() ? EscalationOutcome.EXTENDED : EscalationOutcome.APPLIED);
            }
        };
    }

    /**
     * The span a mute by {@code actor} actually gets once their tier cap is applied. A permanent mute by a
     * capped actor becomes the far-future span clamped to the cap, i.e. a timed mute.
     */
    private Plan plan(PlayerRef actor, SanctionDuration.Parsed parsed) {
        Duration requested = parsed.duration().orElse(Ban.PERMANENT_SPAN);
        Duration span = limit.cap(actor, "mute", requested);
        boolean capped = span.compareTo(requested) < 0;
        return new Plan(span, capped, parsed.duration().isPresent() || capped);
    }

    /** A mute's capped span, whether the cap shortened it, and whether it ends at all. */
    private record Plan(Duration span, boolean capped, boolean timed) {

        /** The span a timed mute runs for; empty for a permanent one. */
        Optional<Duration> effective() {
            return timed ? Optional.of(span) : Optional.empty();
        }
    }

    private static MuteState proposal(
            PlayerRef actor, Plan plan, Optional<String> reason, Optional<DrawnReason> drawn, Instant now) {
        Issuer issuer = Issuer.of(actor);
        return plan.timed()
                ? MuteState.timed(now.plus(plan.span()), issuer, reason, now, drawn)
                : MuteState.permanent(issuer, reason, now, drawn);
    }

    /** Everything a stored mute sets off: the history row, the target's notice, the sync, the event and the notices. */
    private void announce(
            PlayerRef actor,
            PlayerRef told,
            PlayerRef target,
            MuteState mute,
            Plan plan,
            Optional<String> reason,
            Optional<DrawnReason> drawn,
            boolean silent,
            Instant now) {
        Duration span = plan.span();
        Optional<Duration> effective = plan.effective();
        history.mute(actor, target, reason, drawn, effective.map(now::plus));
        notifyTarget(target, effective, reason);
        // A peer re-reads the mute from the shared DB on the next chat attempt; this frame is published so a
        // future mute-caching peer has an invalidation hook (a no-op today and with no bus).
        sync.muteChanged(target);
        events.publish(new PlayerMuted(target, mute, now));
        audit.muted(actor, target, effective.map(SanctionDuration::format), true, reason);
        if (plan.capped()) {
            notifier.send(told, ModerationMessageKey.MOD_DURATION_CAPPED, Map.of("cap", SanctionDuration.format(span)));
        }
        confirmToActor(told, target, effective);
        if (!silent) {
            broadcastMute(actor, target, reason, drawn, effective);
        }
    }

    /**
     * Tell the person who ran the command that it landed, timed or permanent. Separate from the staff
     * broadcast and never suppressed with it: a silent mute hides the sanction from the channel, not from
     * the operator, and while this was missing a {@code /mute -s} looked exactly like a command that did
     * nothing. Ban, warn, unmute and unjail have always answered this way.
     */
    private void confirmToActor(PlayerRef actor, PlayerRef target, Optional<Duration> effective) {
        effective.ifPresentOrElse(
                span -> notifier.send(
                        actor,
                        ModerationMessageKey.MUTE_APPLIED_TIMED,
                        Map.of("player", target.name(), "duration", SanctionDuration.format(span))),
                () -> notifier.send(actor, ModerationMessageKey.MUTE_APPLIED, Map.of("player", target.name())));
    }

    private void broadcastMute(
            PlayerRef actor,
            PlayerRef target,
            Optional<String> reason,
            Optional<DrawnReason> drawn,
            Optional<Duration> effective) {
        broadcast.announce(
                ModerationMessageKey.MOD_BROADCAST_MUTE,
                Map.of(
                        "actor", actor.name(),
                        "target", target.name(),
                        "reason", reason.orElse(""),
                        "duration", effective.map(SanctionDuration::format).orElse(SanctionBroadcast.PERMANENT)),
                drawn);
    }

    /** The word for a sanction with no end, in {@code viewer}'s language: every catalogue translates it. */
    private String permanent(PlayerRef viewer) {
        return notifier.render(viewer, ModerationMessageKey.MOD_GUI_VALUE_PERMANENT, Map.of());
    }

    private void notifyTarget(PlayerRef target, Optional<Duration> effective, Optional<String> reason) {
        Map<String, String> ph = Map.of(
                "duration", effective.map(SanctionDuration::format).orElseGet(() -> permanent(target)),
                "reason", reason.orElse(""));
        notifier.send(target, ModerationMessageKey.MUTE_NOTIFY_TARGET, ph);
    }

    private <T> Result<T, ModerationError> reject(
            PlayerRef actor,
            PlayerRef told,
            PlayerRef target,
            String rawDuration,
            Optional<String> reason,
            ModerationError error) {
        audit.muted(actor, target, label(rawDuration), false, reason);
        notifier.send(told, error.messageKey());
        return Result.err(error);
    }

    /** The duration a refused mute is audited with: what was typed, or none for a permanent request. */
    private static Optional<String> label(String rawDuration) {
        return rawDuration.isBlank() ? Optional.empty() : Optional.of(rawDuration);
    }
}
