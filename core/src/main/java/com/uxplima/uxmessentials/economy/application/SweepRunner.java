package com.uxplima.uxmessentials.economy.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.economy.application.port.AccountSweep;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.Step;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.Sweep;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.SweepOutcome;
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
 * {@link BalancesSwept} event. A chunk that fails changed nothing; the sweep pauses there, the actor is told how far
 * it got, and the runner tries again after {@link #RETRY_AFTER}, for as long as it takes. A sweep left running by a
 * stop is picked up by {@link #resumeUnfinished} when the economy starts on any server. None of this can change an
 * account twice: the journal's cursor moves in the same transaction as the balances.
 *
 * <p>One new sweep at a time per server, so a second click on the bulk screen cannot pay everyone twice. Every method
 * runs off the tick thread.
 */
public final class SweepRunner {

    /** How long a paused sweep waits before it tries its chunk again. */
    static final Duration RETRY_AFTER = Duration.ofMinutes(1);

    private final AccountSweep sweeps;
    private final Scheduler scheduler;
    private final EconomyAudit audit;
    private final EconomyNotifier notifier;
    private final DomainEventPublisher events;
    private final Logger log;
    private final Clock clock;
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private final Set<String> toldPaused = ConcurrentHashMap.newKeySet();
    private final Object starting = new Object();

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
     * Open a sweep and run it here to its end. Returns its totals when this call finished it; null when another sweep
     * is still running on this server (the actor is told), or when the sweep paused and will carry on by itself.
     */
    @Nullable SweepOutcome start(SweepKind kind, SweepScope scope, Money amount, EconomyReason reason, PlayerRef actor) {
        Sweep sweep;
        synchronized (starting) {
            if (!running.isEmpty()) {
                notifier.send(actor, EconomyMessageKey.ECO_ADMIN_BULK_RUNNING, Map.of());
                return null;
            }
            try {
                sweep = sweeps.open(kind, scope, amount, reason, actor, clock.millis());
            } catch (RuntimeException failure) {
                // Nothing was opened, so nothing changed and nothing will resume.
                notifier.send(actor, EconomyMessageKey.ECO_ADMIN_BULK_FAILED, Map.of());
                throw failure;
            }
            running.add(sweep.id());
        }
        return runClaimed(sweep);
    }

    /** Carry on every sweep the journal still has running, here and one after another. Called when the economy starts. */
    public void resumeUnfinished() {
        for (Sweep sweep : sweeps.unfinished()) {
            log.info(
                    "event=economy_sweep_resumed id={} kind={} currency={}",
                    sweep.id(),
                    sweep.kind(),
                    sweep.amount().currency().id().value());
            run(sweep);
        }
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
                    case ALREADY_FINISHED -> {
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
        if (toldPaused.add(sweep.id())) {
            notifier.send(
                    sweep.actor(),
                    EconomyMessageKey.ECO_ADMIN_BULK_PAUSED,
                    Map.of("count", Integer.toString(sofar.changed())));
        }
        scheduler.asyncAfter(RETRY_AFTER, () -> run(sweep));
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
