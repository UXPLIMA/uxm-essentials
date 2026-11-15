package com.uxplima.uxmessentials.moderation.application;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.domain.ModerationError;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation.EscalationStep;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;

/**
 * Turns a freshly-issued warning's running count into an automatic follow-up sanction, per the configured
 * {@link WarnEscalation} ladder. Both {@code /warn} and {@code /tempwarn} run their post-append count through
 * {@link #escalate}: when a rung matches the count exactly, the rung's sanction is applied to the target and
 * the warning's actor is told what it did.
 *
 * <p>The escalated sanction is driven through the existing {@link Mute}/{@link TempBan}/{@link Ban}/{@link Kick}
 * use cases, never back through a warn, so escalation cannot recurse: a tempban from the 5th warning is a
 * plain tempban, it does not re-enter the ladder. It is issued under a stable system actor (so the audit and
 * broadcast attribute it to the server's rule, not the staff member who happened to land the final warning),
 * and inherits the warning's {@code silent} flag, so a silent warn escalates silently.
 *
 * <p>A rung leaves the player under the stronger of its own sanction and the one of that kind already running:
 * a longer mute or ban replaces a shorter one, and a running one that lasts as long or longer (a permanent one
 * above all) is never cut short. Rungs fire while an earlier rung's sanction still runs, so this is the normal
 * case on a tight ladder rather than an edge. Because the use cases answer the system actor, the staff member
 * hears the real outcome from here: applied, extended, already under a stronger sanction, or failed.
 */
public final class WarnEscalator {

    /** The stable system actor an escalated sanction is attributed to. */
    private static final PlayerRef SYSTEM_ACTOR = PlayerRef.system("system");

    private final WarnEscalation ladder;
    private final Mute mute;
    private final TempBan tempBan;
    private final Ban ban;
    private final Kick kick;
    private final Notifier notifier;

    public WarnEscalator(WarnEscalation ladder, Mute mute, TempBan tempBan, Ban ban, Kick kick, Notifier notifier) {
        this.ladder = Objects.requireNonNull(ladder, "ladder");
        this.mute = Objects.requireNonNull(mute, "mute");
        this.tempBan = Objects.requireNonNull(tempBan, "tempBan");
        this.ban = Objects.requireNonNull(ban, "ban");
        this.kick = Objects.requireNonNull(kick, "kick");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * Apply the escalation rung that {@code newWarnCount} trips, if any, against {@code target}; tell
     * {@code actor} which action the rung carried and how it landed. A {@code silent} warning escalates
     * silently. No rung for the count is a no-op.
     */
    public void escalate(PlayerRef actor, PlayerRef target, int newWarnCount, boolean silent) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Optional<EscalationStep> step = ladder.stepFor(newWarnCount);
        if (step.isEmpty()) {
            return;
        }
        EscalationStep rung = step.get();
        Result<EscalationOutcome, ModerationError> outcome = applyAction(target, rung, silent);
        notifier.send(
                actor,
                messageFor(outcome),
                Map.of("action", rung.action().name().toLowerCase(Locale.ROOT)));
    }

    private Result<EscalationOutcome, ModerationError> applyAction(
            PlayerRef target, EscalationStep rung, boolean silent) {
        Optional<String> noReason = Optional.empty();
        String span = rung.duration().map(SanctionDuration::format).orElse("");
        return switch (rung.action()) {
            case MUTE -> mute.escalate(SYSTEM_ACTOR, target, "", noReason, silent);
            case TEMPMUTE -> mute.escalate(SYSTEM_ACTOR, target, span, noReason, silent);
            // A kick has nothing to outlast: it either happened or it did not.
            case KICK -> kick.kick(SYSTEM_ACTOR, target, noReason, silent).map(done -> EscalationOutcome.APPLIED);
            case TEMPBAN -> tempBan.escalate(SYSTEM_ACTOR, target, span, noReason, silent);
            case BAN -> ban.escalate(SYSTEM_ACTOR, target, noReason, silent);
        };
    }

    /**
     * The line the staff member reads. A refusal itself went to the system actor, so here it only says the rung
     * did not land; the console holds the reason.
     */
    private static MessageKey messageFor(Result<EscalationOutcome, ModerationError> outcome) {
        return outcome.asValue()
                .map(landed -> switch (landed) {
                    case APPLIED -> ModerationMessageKey.MOD_WARN_ESCALATED;
                    case EXTENDED -> ModerationMessageKey.MOD_WARN_ESCALATED_EXTENDED;
                    case ALREADY_STRONGER -> ModerationMessageKey.MOD_WARN_ESCALATED_HELD;
                })
                .orElse(ModerationMessageKey.MOD_WARN_ESCALATION_FAILED);
    }
}
