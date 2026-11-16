package com.uxplima.uxmessentials.economy.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.economy.application.port.AccountSweep;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.Step;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.Sweep;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.SweepOutcome;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.SweepStatus;
import com.uxplima.uxmessentials.economy.application.port.EconomyAudit;
import com.uxplima.uxmessentials.economy.domain.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SweepKind;
import com.uxplima.uxmessentials.economy.domain.SweepScope;
import com.uxplima.uxmessentials.economy.domain.event.BalancesSwept;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;

/**
 * Runs sweeps over every account to their end: the work behind {@code /eco giveall} and {@code /eco resetall}.
 *
 * <p>A sweep is advanced one chunk at a time, each its own short transaction, so the runner holds no lock between
 * chunks. Whoever runs the last chunk announces the result once: one audit line, the count to the actor, and one
 * {@link BalancesSwept} event. A chunk that fails for a reason that passes (a lost connection, a lock cycle) changed
 * nothing; the sweep is journalled as paused, the actor is told how far it got, and the runner tries again after
 * {@link #RETRY_AFTER} until it finishes or an operator cancels it. A chunk the database refuses for good ends the
 * sweep as failed, with one audit line for the accounts already changed. A sweep left running by a stop is picked up
 * by {@link #resumeUnfinished} when the economy starts on any server. None of this can change an account twice: the
 * journal's cursor moves in the same transaction as the balances.
 *
 * <p>The journal keeps one unfinished sweep per currency across every server, so a second click on the bulk screen,
 * or the command repeated after a pause, is refused rather than paying everyone twice. Every method runs off the tick
 * thread.
 */
public final class SweepRunner {

    /** How long a paused sweep waits before it tries its chunk again. */
    static final Duration RETRY_AFTER = Duration.ofMinutes(1);

    /** How many characters of a sweep id are shown. */
    private static final int SHORT_ID = 8;

    private final AccountSweep sweeps;
    private final Scheduler scheduler;
    private final EconomyAudit audit;
    private final EconomyNotifier notifier;
    private final DomainEventPublisher events;
    private final Logger log;
    private final Clock clock;
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private final Set<String> toldPaused = ConcurrentHashMap.newKeySet();

    public SweepRunner(
            AccountSweep sweeps,
            Scheduler scheduler,
            EconomyAudit audit,
            EconomyNotifier notifier,
            DomainEventPublisher events,
            Logger log,
            Clock clock) {
        this.sweeps = Objects.requireNonNull(sweeps, "sweeps");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Whether the currency's balances can be swept at all. */
    boolean reaches(com.uxplima.uxmessentials.economy.domain.Currency currency) {
        return sweeps.reaches(currency);
    }

    /**
     * Open a sweep and run it here to its end. Returns its totals when this call finished it; null when the currency
     * already has an unfinished sweep (the actor is told which), or when the sweep paused or failed.
     */
    @Nullable SweepOutcome start(SweepKind kind, SweepScope scope, Money amount, EconomyReason reason, PlayerRef actor) {
        AccountSweep.Opening opening;
        try {
            opening = sweeps.open(kind, scope, amount, reason, actor, clock.millis());
        } catch (RuntimeException failure) {
            // Nothing was opened, so nothing changed and nothing will resume.
            notifier.send(actor, EconomyMessageKey.ECO_ADMIN_BULK_FAILED, Map.of());
            throw failure;
        }
        return switch (opening) {
            case AccountSweep.Opened opened -> run(opened.sweep());
            case AccountSweep.Busy busy -> {
                SweepStatus existing = busy.existing();
                notifier.send(
                        actor,
                        EconomyMessageKey.ECO_ADMIN_BULK_RUNNING,
                        Map.of(
                                "id",
                                shortId(existing.id()),
                                "currency",
                                existing.currency(),
                                "state",
                                existing.state().name().toLowerCase(Locale.ROOT)));
                yield null;
            }
        };
    }

    /** Carry on every sweep the journal still has running or paused, here and one after another. */
    public void resumeUnfinished() {
        for (Sweep sweep : sweeps.resumable()) {
            log.info(
                    "event=economy_sweep_resumed id={} kind={} currency={}",
                    sweep.id(),
                    sweep.kind(),
                    sweep.amount().currency().id().value());
            run(sweep);
        }
    }

    /** Every sweep still running or paused, on any server. */
    List<SweepStatus> unfinished() {
        return sweeps.unfinished();
    }

    /**
     * Stop the unfinished sweep {@code id} (or the one id that starts with it). The accounts it changed keep the
     * change; a runner still working on it stops at its next chunk.
     */
    Optional<SweepStatus> cancel(PlayerRef by, String id) {
        Optional<SweepStatus> cancelled = sweeps.cancel(id, clock.millis());
        cancelled.ifPresent(status -> {
            toldPaused.remove(status.id());
            log.info(
                    "event=economy_sweep_cancelled id={} by={} changed={}",
                    status.id(),
                    by.name(),
                    status.total().changed());
        });
        return cancelled;
    }

    /** The part of a sweep id an operator reads and types; any unique start of it is accepted. */
    static String shortId(String id) {
        return id.length() <= SHORT_ID ? id : id.substring(0, SHORT_ID);
    }

    /** Run {@code sweep} to its end here, unless this server already runs it. */
    @Nullable SweepOutcome run(Sweep sweep) {
        if (!running.add(sweep.id())) {
            return null;
        }
        return runClaimed(sweep);
    }

    private @Nullable SweepOutcome runClaimed(Sweep sweep) {
        SweepOutcome sofar = SweepOutcome.none();
        try {
            while (true) {
                Step step;
                try {
                    step = sweeps.advance(sweep);
                } catch (RuntimeException failure) {
                    paused(sweep, sofar, failure);
                    return null;
                }
                sofar = step.total();
                switch (step.progress()) {
                    case MORE -> {
                        // The next chunk opens its own transaction; nothing is held in between.
                    }
                    case FINISHED -> {
                        finished(sweep, step.total());
                        return step.total();
                    }
                    case ENDED -> {
                        toldPaused.remove(sweep.id());
                        return null;
                    }
                    case FAILED -> {
                        failed(sweep, step);
                        return null;
                    }
                }
            }
        } finally {
            running.remove(sweep.id());
        }
    }

    private void paused(Sweep sweep, SweepOutcome sofar, RuntimeException failure) {
        log.error("event=economy_sweep_paused id=" + sweep.id() + " changed=" + sofar.changed(), failure);
        try {
            sweeps.pause(sweep, describe(failure));
        } catch (RuntimeException unrecorded) {
            // The journal is as unreachable as the chunk was; the sweep still reads as running and is retried.
        }
        if (toldPaused.add(sweep.id())) {
            notifier.send(
                    sweep.actor(),
                    EconomyMessageKey.ECO_ADMIN_BULK_PAUSED,
                    Map.of("count", Integer.toString(sofar.changed())));
        }
        scheduler.asyncAfter(RETRY_AFTER, () -> run(sweep));
    }

    private void failed(Sweep sweep, Step step) {
        toldPaused.remove(sweep.id());
        SweepOutcome total = step.total();
        log.error(
                "event=economy_sweep_failed id=" + sweep.id() + " changed=" + total.changed() + " error="
                        + step.error(),
                new IllegalStateException("sweep " + sweep.id() + " refused by the database"));
        audit.bulkMutation(sweep.actor(), sweep.amount(), total.changed(), sweep.reason());
        notifier.send(
                sweep.actor(),
                EconomyMessageKey.ECO_ADMIN_BULK_HALTED,
                Map.of("count", Integer.toString(total.changed())));
    }

    /** One line saying what failed, for the journal an operator reads. */
    private static String describe(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private void finished(Sweep sweep, SweepOutcome total) {
        toldPaused.remove(sweep.id());
        PlayerRef actor = sweep.actor();
        Money amount = sweep.amount();
        audit.bulkMutation(actor, amount, total.changed(), sweep.reason());
        if (sweep.kind() == SweepKind.CREDIT) {
            notifier.send(
                    actor,
                    EconomyMessageKey.ECO_ADMIN_GIVEALL,
                    Map.of("count", Integer.toString(total.changed()), "amount", notifier.amount(amount)));
            if (total.skipped() > 0) {
                notifier.send(
                        actor,
                        EconomyMessageKey.ECO_ADMIN_GIVEALL_CAPPED,
                        Map.of("count", Integer.toString(total.skipped())));
            }
        } else {
            notifier.send(
                    actor, EconomyMessageKey.ECO_ADMIN_RESETALL, Map.of("count", Integer.toString(total.changed())));
        }
        if (total.gone() > 0) {
            notifier.send(
                    actor, EconomyMessageKey.ECO_ADMIN_BULK_GONE, Map.of("count", Integer.toString(total.gone())));
        }
        events.publish(new BalancesSwept(
                sweep.id(),
                sweep.kind(),
                sweep.scope(),
                amount,
                total.changed(),
                total.skipped(),
                actor,
                clock.instant()));
    }
}
