package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.uxplima.uxmessentials.economy.application.port.AccountSweep;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.Step;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.Sweep;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.SweepOutcome;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.SweepStatus;
import com.uxplima.uxmessentials.economy.application.port.EconomyAudit;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.KnownPlayers;
import com.uxplima.uxmessentials.economy.application.port.ProviderLane;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SweepKind;
import com.uxplima.uxmessentials.economy.domain.SweepMode;
import com.uxplima.uxmessentials.economy.domain.SweepScope;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.event.BalancesSwept;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.Nullable;

/**
 * Runs sweeps over every account to their end: the work behind {@code /eco giveall} and {@code /eco resetall}, for
 * every currency setup.
 *
 * <p>Where the money is kept decides the {@link SweepMode}. On this plugin's ledger each chunk changes the balances
 * itself. For an item currency each chunk queues one delivery per player, and this runner then hands over the ones
 * for players online here ({@link Paths#itemsQueued}); the rest are handed over on their next join. When another
 * plugin keeps the balances (a Vault or Treasury economy, or a currency backend of another plugin), each chunk hands
 * out a group of players, this runner pays them one by one through that plugin on the lane it expects
 * ({@link ProviderLane}), and then settles the chunk in the journal.
 *
 * <p>Calls to another plugin are not transactional, so a provider sweep is at most once: a player is handed out
 * before the call, and a runner that stops before settling leaves those players uncertain. They are counted and
 * logged by name, never paid again: a missed payment can be made up from the log, a doubled one cannot be found.
 *
 * <p>A chunk is its own short transaction, so the runner holds no lock between chunks. Whoever runs the last chunk
 * announces the result once: one audit line, the count to the actor, and one {@link BalancesSwept} event. A chunk
 * that fails for a reason that passes changed nothing; the sweep is journalled as paused, the actor is told how far
 * it got, and the runner tries again after {@link #RETRY_AFTER} until it finishes or an operator cancels it. A chunk
 * the database refuses for good ends the sweep as failed. A sweep left running by a stop is picked up by
 * {@link #resumeUnfinished} when the economy starts on any server.
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
    private final Paths paths;
    private final Scheduler scheduler;
    private final EconomyAudit audit;
    private final EconomyNotifier notifier;
    private final DomainEventPublisher events;
    private final Logger log;
    private final Clock clock;
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private final Set<String> toldPaused = ConcurrentHashMap.newKeySet();
    /** Provider chunks whose calls were made but whose settle has not been stored yet, by sweep id. */
    private final Map<String, SweepOutcome> unsettled = new ConcurrentHashMap<>();

    public SweepRunner(
            AccountSweep sweeps,
            Paths paths,
            Scheduler scheduler,
            EconomyAudit audit,
            EconomyNotifier notifier,
            DomainEventPublisher events,
            Logger log,
            Clock clock) {
        this.sweeps = Objects.requireNonNull(sweeps, "sweeps");
        this.paths = Objects.requireNonNull(paths, "paths");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * How a sweep reaches each kind of currency.
     *
     * @param modes where a currency's money is kept, as a sweep reaches it
     * @param economy the provider in play, for a sweep through another plugin
     * @param history the ledger a sweep through another plugin writes its rows to
     * @param lanes where the calls to the plugin that keeps a currency run
     * @param itemsQueued hands over the deliveries queued for the players online here
     * @param giveNow queues a give for each player online here and hands it over at once, for a currency whose
     *     balance changes only while its player is online
     * @param knownPlayers tops up the list of players a sweep reaches before it opens
     */
    public record Paths(
            Function<Currency, SweepMode> modes,
            EconomyProvider economy,
            TransactionHistory history,
            Function<Currency, ProviderLane> lanes,
            Runnable itemsQueued,
            DeferredGive giveNow,
            KnownPlayers knownPlayers) {

        public Paths {
            Objects.requireNonNull(giveNow, "giveNow");
            Objects.requireNonNull(modes, "modes");
            Objects.requireNonNull(economy, "economy");
            Objects.requireNonNull(history, "history");
            Objects.requireNonNull(lanes, "lanes");
            Objects.requireNonNull(itemsQueued, "itemsQueued");
            Objects.requireNonNull(knownPlayers, "knownPlayers");
        }
    }

    /** Queue a give for each of a list of players and hand it over at once, keeping what does not fit. */
    @FunctionalInterface
    public interface DeferredGive {

        /** Returns how many players a give was queued for. */
        int giveNow(List<PlayerRef> online, Money amount, EconomyReason reason);
    }

    /** Whether {@code currency}'s balances change only while their player is online, so a give is queued first. */
    boolean deferred(Currency currency) {
        return paths.modes().apply(currency) == SweepMode.DEFERRED;
    }

    /** {@code /eco giveall --online} for a deferred currency; returns how many players a give was queued for. */
    int giveOnline(List<PlayerRef> online, Money amount, EconomyReason reason) {
        return paths.giveNow().giveNow(online, amount, reason);
    }

    /**
     * Open a sweep and run it here to its end. Returns its totals when this call finished it; null when the currency
     * already has an unfinished sweep (the actor is told which), or when the sweep paused or failed.
     */
    @Nullable SweepOutcome start(SweepKind kind, SweepScope scope, Money amount, EconomyReason reason, PlayerRef actor) {
        SweepMode mode = paths.modes().apply(amount.currency());
        try {
            paths.knownPlayers().recordAll();
        } catch (RuntimeException failure) {
            // The players every server recorded at their joins are still reached.
            log.error("event=economy_known_players_failed", failure);
        }
        AccountSweep.Opening opening;
        try {
            opening = sweeps.open(kind, scope, mode, amount, reason, actor, clock.millis());
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
                    "event=economy_sweep_resumed id={} kind={} mode={} currency={}",
                    sweep.id(),
                    sweep.kind(),
                    sweep.mode(),
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
                    settleLeftOver(sweep);
                    step = sweeps.advance(sweep);
                } catch (RuntimeException failure) {
                    paused(sweep, sofar, failure);
                    return null;
                }
                sofar = step.total();
                switch (step.progress()) {
                    case MORE -> {
                        if (!step.calls().isEmpty()) {
                            SweepOutcome done = callChunk(sweep, step.calls());
                            sofar = sofar.plus(done);
                            unsettled.put(sweep.id(), done);
                        }
                        if (sweep.mode() == SweepMode.DEFERRED
                                && !step.changedOwners().isEmpty()) {
                            handOverItems();
                        }
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
                    case BUSY -> {
                        // Another server is paying a chunk of it; try again once that chunk has been settled.
                        scheduler.asyncAfter(RETRY_AFTER, () -> run(sweep));
                        return null;
                    }
                }
            }
        } finally {
            running.remove(sweep.id());
        }
    }

    /** Store the outcome of a provider chunk whose settle failed before, so its calls are counted exactly once. */
    private void settleLeftOver(Sweep sweep) {
        SweepOutcome left = unsettled.get(sweep.id());
        if (left != null) {
            sweeps.settle(sweep, left);
            unsettled.remove(sweep.id());
        }
    }

    private void handOverItems() {
        try {
            paths.itemsQueued().run();
        } catch (RuntimeException failure) {
            // The deliveries stay queued for each player's next join.
            log.error("event=swept_items_handover_failed", failure);
        }
    }

    /** Pay one handed-out chunk through the plugin that keeps the balances. */
    private SweepOutcome callChunk(Sweep sweep, List<PlayerRef> owners) {
        ProviderLane lane = paths.lanes().apply(sweep.amount().currency());
        List<Call> calls = lane.each(owners, owner -> call(sweep, owner), Call.NOT_RUN);
        int changed = 0;
        int skipped = 0;
        int uncertain = 0;
        for (int i = 0; i < calls.size(); i++) {
            Call call = calls.get(i);
            switch (call) {
                case CHANGED -> changed++;
                case SKIPPED -> skipped++;
                case UNCHANGED -> {
                    // Already at the figure a reset sets.
                }
                case FAILED, NOT_RUN -> {
                    uncertain++;
                    log.warn(
                            "event=economy_sweep_uncertain id={} owner={} name={} reason={}",
                            sweep.id(),
                            owners.get(i).uuid(),
                            owners.get(i).name(),
                            call == Call.NOT_RUN ? "not_called" : "call_failed");
                }
            }
        }
        return new SweepOutcome(changed, skipped, 0, uncertain);
    }

    /** One player's change through the other plugin, on its lane. */
    private Call call(Sweep sweep, PlayerRef owner) {
        Money amount = sweep.amount();
        Currency currency = amount.currency();
        try {
            if (sweep.kind() == SweepKind.CREDIT) {
                return landed(paths.economy().credit(owner, amount), owner, amount, true, sweep.reason());
            }
            BigDecimal before;
            try {
                before = paths.economy().balance(owner, currency).amount();
            } catch (RuntimeException unknown) {
                // A balance that did not answer is unknown, not zero: setting from a guess would pay or take the
                // wrong amount. Nothing was changed, so the player is counted as skipped.
                log.warn(
                        "event=economy_sweep_balance_unknown id={} owner={} reason={}",
                        sweep.id(),
                        owner.uuid(),
                        unknown.toString());
                return Call.SKIPPED;
            }
            int sign = amount.amount().compareTo(before);
            if (sign == 0) {
                return Call.UNCHANGED;
            }
            Money delta = Money.of(currency, amount.amount().subtract(before).abs());
            return sign > 0
                    ? landed(paths.economy().credit(owner, delta), owner, delta, true, sweep.reason())
                    : landed(paths.economy().debit(owner, delta), owner, delta, false, sweep.reason());
        } catch (RuntimeException failure) {
            log.error("event=economy_sweep_call_failed id=" + sweep.id() + " owner=" + owner.uuid(), failure);
            return Call.FAILED;
        }
    }

    private Call landed(
            Result<Unit, TransferError> result, PlayerRef owner, Money moved, boolean credit, EconomyReason reason) {
        if (result.isErr()) {
            return Call.SKIPPED;
        }
        String ownerId = owner.uuid().toString();
        if (credit) {
            paths.history().recordCredit(ownerId, moved, reason, clock.millis());
        } else {
            paths.history().recordDebit(ownerId, moved, reason, clock.millis());
        }
        return Call.CHANGED;
    }

    /** What one call to the other plugin did. */
    private enum Call {
        CHANGED,
        UNCHANGED,
        SKIPPED,
        FAILED,
        NOT_RUN
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
        String count = Integer.toString(total.changed());
        boolean items = sweep.mode() == SweepMode.DEFERRED;
        if (sweep.kind() == SweepKind.CREDIT) {
            notifier.send(
                    actor,
                    items ? EconomyMessageKey.ECO_ADMIN_GIVEALL_ITEMS : EconomyMessageKey.ECO_ADMIN_GIVEALL,
                    Map.of("count", count, "amount", notifier.amount(amount)));
            if (total.skipped() > 0) {
                notifier.send(
                        actor,
                        EconomyMessageKey.ECO_ADMIN_GIVEALL_CAPPED,
                        Map.of("count", Integer.toString(total.skipped())));
            }
        } else {
            notifier.send(
                    actor,
                    items ? EconomyMessageKey.ECO_ADMIN_RESETALL_ITEMS : EconomyMessageKey.ECO_ADMIN_RESETALL,
                    Map.of("count", count, "amount", notifier.amount(amount)));
        }
        if (total.gone() > 0) {
            notifier.send(
                    actor, EconomyMessageKey.ECO_ADMIN_BULK_GONE, Map.of("count", Integer.toString(total.gone())));
        }
        if (total.uncertain() > 0) {
            notifier.send(
                    actor,
                    EconomyMessageKey.ECO_ADMIN_BULK_UNCERTAIN,
                    Map.of("count", Integer.toString(total.uncertain())));
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
