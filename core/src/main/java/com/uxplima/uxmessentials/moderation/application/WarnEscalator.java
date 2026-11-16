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
 * and inherits the warning's {@code silent} flag, so a silent warn escalates silently. Its reason names the
 * warning count that tripped the step and that warning's own reason, so the history says what caused it.
 *
 * <p>A rung leaves the player under the stronger of its own sanction and the one of that kind already running:
 * a longer mute or ban replaces a shorter one, and a running one that lasts as long or longer (a permanent one
 * above all) is never cut short. Rungs fire while an earlier rung's sanction still runs, so this is the normal
 * case on a tight ladder rather than an edge. What the use cases say about the sanction (that it landed, or why it
 * was refused) goes to the staff member who gave the warning, and this then tells them how the step landed:
 * applied, extended, already under a stronger sanction, or failed.
 */
public final class WarnEscalator {

    /**
     * The longest reason a step's sanction is given: the width of the reason columns its rows are stored in. The
     * reason quotes a staff member's warning, so without this a long warning would make a reason no row can hold.
     */
    static final int MAX_REASON = 256;

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
     * {@code actor} which action the rung carried and how it landed. {@code warnReason} is the reason of the warning
     * that reached the count. A {@code silent} warning escalates silently. No rung for the count is a no-op.
     */
    public void escalate(
            PlayerRef actor, PlayerRef target, int newWarnCount, Optional<String> warnReason, boolean silent) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(warnReason, "warnReason");
        Optional<EscalationStep> step = ladder.stepFor(newWarnCount);
        if (step.isEmpty()) {
            return;
        }
        EscalationStep rung = step.get();
        Optional<String> reason = Optional.of(reasonFor(target, newWarnCount, warnReason));
        Result<EscalationOutcome, ModerationError> outcome = applyAction(actor, target, rung, reason, silent);
        notifier.send(
                actor,
                messageFor(outcome),
                Map.of("action", rung.action().name().toLowerCase(Locale.ROOT)));
    }

    /**
     * The reason a step's sanction is stored, shown and announced with: the count that tripped it and the reason of
     * the warning that reached it, cut to {@link #MAX_REASON} characters. It is resolved for the punished player, who
     * reads it on the kick screen and in the mute notice, so it follows the language the resolver picks for them.
     */
    private String reasonFor(PlayerRef target, int count, Optional<String> warnReason) {
        String total = Integer.toString(count);
        String reason = warnReason
                .filter(text -> !text.isBlank())
                .map(text -> notifier.render(
                        target,
                        ModerationMessageKey.MOD_WARN_ESCALATION_REASON,
                        Map.of("count", total, "reason", text)))
                .orElseGet(() -> notifier.render(
                        target, ModerationMessageKey.MOD_WARN_ESCALATION_REASON_BARE, Map.of("count", total)));
        return capped(reason);
    }

    /** {@code reason} cut to {@link #MAX_REASON} characters, never splitting a surrogate pair. */
    private static String capped(String reason) {
        if (reason.length() <= MAX_REASON) {
            return reason;
        }
        int end = Character.isHighSurrogate(reason.charAt(MAX_REASON - 1)) ? MAX_REASON - 1 : MAX_REASON;
        return reason.substring(0, end);
    }

    private Result<EscalationOutcome, ModerationError> applyAction(
            PlayerRef told, PlayerRef target, EscalationStep rung, Optional<String> reason, boolean silent) {
        String span = rung.duration().map(SanctionDuration::format).orElse("");
        return switch (rung.action()) {
            case MUTE -> mute.escalate(SYSTEM_ACTOR, told, target, "", reason, silent);
            case TEMPMUTE -> mute.escalate(SYSTEM_ACTOR, told, target, span, reason, silent);
            // A kick has nothing to outlast: it either happened or it did not.
            case KICK -> kick.kick(SYSTEM_ACTOR, told, target, reason, silent).map(done -> EscalationOutcome.APPLIED);
            case TEMPBAN -> tempBan.escalate(SYSTEM_ACTOR, told, target, span, reason, silent);
            case BAN -> ban.escalate(SYSTEM_ACTOR, told, target, reason, silent);
        };
    }

    /**
     * The line the staff member reads after the use case's own: it ties that line to the warning step, and says so
     * when the step was held or did not land.
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
