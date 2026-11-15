package com.uxplima.uxmessentials.shared.application.teleport;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;

/**
 * Settles the money of a local teleport that was paid for before its warmup. The charge is taken up front so a
 * player cannot start a teleport they cannot afford, but the teleport can still end without moving them: a move or
 * a hit during the warmup, a newer teleport replacing it, a hop that misses, or the player leaving first. The
 * teleport port reports exactly one {@link TeleportOutcome} for the request, and this keeps the charge only when it
 * is {@link TeleportOutcome#LANDED}; any other outcome gives it back once.
 *
 * <p>The outcome can arrive on the player's region thread (a move cancels the warmup there, and the landing is
 * reported there), while giving money back may write to the database. So the settling always runs on the async lane.
 *
 * <p>Nothing here throws back into the teleport. The charge is already spent when this runs, so a fault is logged
 * with the player and the amount for an operator to settle by hand. Money moves before the player is told, so a
 * failing message never costs them their refund.
 */
public final class PaidTeleports {

    private final Scheduler scheduler;
    private final Logger log;

    public PaidTeleports(Scheduler scheduler, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** The operator line that names a paid teleport: what it was for, who paid, and how much. */
    public static String subject(String kind, String target, PlayerRef payer, BigDecimal amount, String currencyId) {
        return "kind=" + kind + " target=" + target + " player=" + payer.uuid() + " name=" + payer.name() + " amount="
                + amount.toPlainString() + " currency=" + currencyId;
    }

    /**
     * Once {@code outcome} is known, keep the charge if the player landed, otherwise {@code refund} it and, when the
     * credit landed, run {@code refunded} to tell the player.
     */
    public void settle(
            CompletionStage<TeleportOutcome> outcome, String subject, BooleanSupplier refund, Runnable refunded) {
        settle(outcome, subject, () -> true, refund, refunded);
    }

    /**
     * As {@link #settle(CompletionStage, String, BooleanSupplier, Runnable)}, but a landing first runs {@code keep},
     * the step that pays the money on to whoever earns it. A {@code keep} that answers false paid nobody, so the
     * charge is given back rather than left with no one: the player has moved, but the money must not vanish.
     */
    public void settle(
            CompletionStage<TeleportOutcome> outcome,
            String subject,
            BooleanSupplier keep,
            BooleanSupplier refund,
            Runnable refunded) {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(keep, "keep");
        Objects.requireNonNull(refund, "refund");
        Objects.requireNonNull(refunded, "refunded");
        var ignored = outcome.whenComplete((ended, failure) -> {
            if (ended == null) {
                // A port that breaks its promise leaves no way to tell whether the player moved, so the charge is
                // neither kept knowingly nor refunded twice: the operator gets the trace to settle it.
                log.error(
                        "event=paid_teleport_outcome_unknown " + subject + " outcome=charge_kept", orUnknown(failure));
                return;
            }
            offTick(subject, () -> afterOutcome(ended, subject, keep, refund, refunded));
        });
    }

    private void afterOutcome(
            TeleportOutcome ended, String subject, BooleanSupplier keep, BooleanSupplier refund, Runnable refunded) {
        if (!ended.landed()) {
            giveBack(subject, "outcome=" + ended.name().toLowerCase(Locale.ROOT), refund, refunded);
            return;
        }
        boolean kept;
        try {
            kept = keep.getAsBoolean();
        } catch (RuntimeException keepFailure) {
            log.error("event=paid_teleport_settle_threw " + subject + " outcome=charge_kept", keepFailure);
            return;
        }
        if (!kept) {
            log.warn("event=paid_teleport_settle_failed {} outcome=player_refunded", subject);
            giveBack(subject, "outcome=landed_unsettled", refund, refunded);
        }
    }

    private void giveBack(String subject, String why, BooleanSupplier refund, Runnable refunded) {
        String line = "event=paid_teleport_refund_failed " + subject + " " + why;
        try {
            if (!refund.getAsBoolean()) {
                log.error(line, new IllegalStateException("refund rejected"));
                return;
            }
        } catch (RuntimeException refundFailure) {
            log.error(line, refundFailure);
            return;
        }
        try {
            refunded.run();
        } catch (RuntimeException noticeFailure) {
            log.warn("event=paid_teleport_refund_notice_failed {} reason={}", subject, noticeFailure.toString());
        }
    }

    private void offTick(String subject, Runnable task) {
        try {
            scheduler.async(() -> {
                try {
                    task.run();
                } catch (RuntimeException settleFailure) {
                    log.error("event=paid_teleport_settle_threw " + subject, settleFailure);
                }
            });
        } catch (RuntimeException refused) {
            // The async lane refuses work only while the plugin stops; running the refund here could put a database
            // write on a region thread, so the line is what an operator settles it from.
            log.error("event=paid_teleport_settle_refused " + subject, refused);
        }
    }

    private static Throwable orUnknown(@Nullable Throwable failure) {
        return failure != null ? failure : new IllegalStateException("the teleport completed without an outcome");
    }
}
