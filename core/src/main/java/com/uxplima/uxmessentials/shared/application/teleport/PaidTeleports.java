package com.uxplima.uxmessentials.shared.application.teleport;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;

/**
 * Settles the money of a local teleport that was paid for before its warmup. The charge is taken up front so a
 * player cannot start a teleport they cannot afford, but the teleport can still end without moving them: a move or
 * a hit during the warmup, a newer teleport replacing it, a hop that misses, or the player leaving first. The use
 * case opens a {@link Settlement} as soon as the charge is taken, before anything else can fail, and the settlement
 * keeps the charge only when the teleport is {@link TeleportOutcome#LANDED}; any other outcome gives it back once.
 *
 * <p>The outcome can arrive on the player's region thread (a move cancels the warmup there, and the landing is
 * reported there), while giving money back may write to the database. So the settling always runs on the async lane.
 *
 * <p>Nothing here throws back into the teleport. The charge is already spent when this runs, so a fault is logged
 * with the player and the amount for an operator to settle by hand. Money moves before the player is told, so a
 * failing message never costs them their refund.
 *
 * <p>A settlement still open when the module stops is logged as {@code event=paid_teleport_unsettled} rather than
 * refunded there. The stop runs on the server thread, where the economy port may write to the database, and a
 * warmup cut short by the stop may still be reported afterwards; the line carries everything an operator needs, and
 * the settlement is then closed so a late outcome cannot pay a second time.
 */
public final class PaidTeleports {

    private final Scheduler scheduler;
    private final Logger log;
    private final Set<Settlement> open = ConcurrentHashMap.newKeySet();

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
     * Open the settlement of a charge just taken: once its outcome is known, keep the charge if the player landed,
     * otherwise {@code refund} it and, when the credit landed, run {@code refunded} to tell the player.
     */
    public Settlement open(String subject, BooleanSupplier refund, Runnable refunded) {
        return open(subject, () -> true, refund, refunded);
    }

    /**
     * As {@link #open(String, BooleanSupplier, Runnable)}, but a landing first runs {@code keep}, the step that pays
     * the money on to whoever earns it. A {@code keep} that answers false or throws is treated as one that paid
     * nobody, the same rule the cross-server settlement follows, so the charge is given back rather than left with no
     * one: the player has moved, but the money must not vanish.
     */
    public Settlement open(String subject, BooleanSupplier keep, BooleanSupplier refund, Runnable refunded) {
        Settlement settlement = new Settlement(
                Objects.requireNonNull(subject, "subject"),
                Objects.requireNonNull(keep, "keep"),
                Objects.requireNonNull(refund, "refund"),
                Objects.requireNonNull(refunded, "refunded"));
        open.add(settlement);
        var ignored = settlement.outcome.whenComplete(settlement::ended);
        return settlement;
    }

    /** Log every settlement still waiting on its teleport; called when the module stops. */
    public void close() {
        for (Settlement pending : List.copyOf(open)) {
            if (open.remove(pending)) {
                log.error(
                        "event=paid_teleport_unsettled " + pending.subject + " outcome=charge_kept",
                        new IllegalStateException("the module stopped before the teleport ended"));
            }
        }
    }

    private void afterOutcome(Settlement settled, TeleportOutcome ended) {
        if (!ended.landed()) {
            giveBack(settled, "outcome=" + ended.name().toLowerCase(Locale.ROOT));
            return;
        }
        boolean kept;
        try {
            kept = settled.keep.getAsBoolean();
        } catch (RuntimeException keepFailure) {
            log.error("event=paid_teleport_settle_threw " + settled.subject + " outcome=player_refunded", keepFailure);
            giveBack(settled, "outcome=landed_unsettled");
            return;
        }
        if (!kept) {
            log.warn("event=paid_teleport_settle_failed {} outcome=player_refunded", settled.subject);
            giveBack(settled, "outcome=landed_unsettled");
        }
    }

    private void giveBack(Settlement settled, String why) {
        String line = "event=paid_teleport_refund_failed " + settled.subject + " " + why;
        try {
            if (!settled.refund.getAsBoolean()) {
                log.error(line, new IllegalStateException("refund rejected"));
                return;
            }
        } catch (RuntimeException refundFailure) {
            log.error(line, refundFailure);
            return;
        }
        try {
            settled.refunded.run();
        } catch (RuntimeException noticeFailure) {
            log.warn(
                    "event=paid_teleport_refund_notice_failed {} reason={}", settled.subject, noticeFailure.toString());
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

    /**
     * The money of one paid teleport, waiting on how it ended. The first outcome wins: a teleport that is refused
     * before it starts and then reports again, or a stage followed after a refusal, settles once.
     */
    public final class Settlement {

        private final String subject;
        private final BooleanSupplier keep;
        private final BooleanSupplier refund;
        private final Runnable refunded;
        private final CompletableFuture<TeleportOutcome> outcome = new CompletableFuture<>();

        private Settlement(String subject, BooleanSupplier keep, BooleanSupplier refund, Runnable refunded) {
            this.subject = subject;
            this.keep = keep;
            this.refund = refund;
            this.refunded = refunded;
        }

        /** Settle on whatever {@code teleport} reports. */
        public void follow(CompletionStage<TeleportOutcome> teleport) {
            Objects.requireNonNull(teleport, "teleport");
            var ignored = teleport.whenComplete((ended, failure) -> {
                if (ended != null) {
                    outcome.complete(ended);
                } else {
                    outcome.completeExceptionally(orUnknown(failure));
                }
            });
        }

        /** The teleport never started: something failed between the charge and the launch. */
        public void refuse() {
            outcome.complete(TeleportOutcome.REFUSED);
        }

        private void ended(@Nullable TeleportOutcome ended, @Nullable Throwable failure) {
            if (!open.remove(this)) {
                return; // closed at stop, where it was already logged
            }
            if (ended == null) {
                // A port that breaks its promise leaves no way to tell whether the player moved, so the charge is
                // neither kept knowingly nor refunded twice: the operator gets the trace to settle it.
                log.error(
                        "event=paid_teleport_outcome_unknown " + subject + " outcome=charge_kept", orUnknown(failure));
                return;
            }
            offTick(subject, () -> afterOutcome(this, ended));
        }
    }
}
