package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Transactions.TRANSACTIONS;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep8;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The append-only ledger-history writer for the {@code transactions} table. Unlike the debounced settle queue
 * ({@link DebouncedWalletWriter}), telemetry rows must <strong>all</strong> survive — none may collapse — so
 * they buffer in memory and flush as one batch {@code INSERT} on a fixed interval
 * ({@code docs/11-economy-integration.md} §10.1: debounce collapses, batch preserves). Each row records one
 * applied money move with its currency, type, and {@link EconomyReason} stored as their stable enum names so
 * an audit review can {@code GROUP BY} them.
 *
 * <p>This is the ledger, separate from the operator audit channel. The buffer drains synchronously on
 * {@link #stop()} so a clean shutdown never strands history.
 */
@NullMarked
public final class TransactionTelemetry extends JooqRepository {

    private final Queue<Row> buffer = new ConcurrentLinkedQueue<>();
    private final AtomicLong ids = new AtomicLong(System.currentTimeMillis() * 1_000L);
    private final Scheduler scheduler;
    private final Logger log;
    private final Duration interval;
    private volatile boolean running;

    public TransactionTelemetry(DSLContext dsl, Scheduler scheduler, Logger log, Duration interval) {
        super(dsl);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.interval = Objects.requireNonNull(interval, "interval");
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("batch-flush interval must be positive: " + interval);
        }
    }

    /** Arm the batch-flush loop. Idempotent so a reload re-arm is harmless. */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        scheduleNext();
    }

    /** Record a single-sided credit to {@code owner}. */
    public void credit(PlayerRef owner, Money amount, EconomyReason reason, long at) {
        buffer.add(Row.single(nextId(), at, owner, amount, "CREDIT", reason));
    }

    /** Record a single-sided debit from {@code owner}. */
    public void debit(PlayerRef owner, Money amount, EconomyReason reason, long at) {
        buffer.add(Row.single(nextId(), at, owner, amount, "DEBIT", reason));
    }

    /** Record an atomic transfer carrying both sides. */
    public void transfer(PlayerRef from, PlayerRef to, Money amount, EconomyReason reason, long at) {
        buffer.add(Row.transfer(nextId(), at, from, to, amount, reason));
    }

    /** Write every buffered row as one batch {@code INSERT}. Runs off-tick on the flush loop. */
    public void flush() {
        List<Row> drained = drain();
        if (drained.isEmpty()) {
            return;
        }
        try {
            write(dsl -> {
                batch(dsl, drained).execute();
                return null;
            });
        } catch (RuntimeException cause) {
            log.warn("transaction telemetry batch of {} rows failed to flush", drained.size());
        }
    }

    /** Drain synchronously and stop the loop. Called on module stop and before a reload swap. */
    public void stop() {
        running = false;
        flush();
    }

    private List<Row> drain() {
        List<Row> drained = new ArrayList<>();
        for (Row row = buffer.poll(); row != null; row = buffer.poll()) {
            drained.add(row);
        }
        return drained;
    }

    private InsertValuesStep8<?, Long, Long, String, String, String, BigDecimal, String, String> batch(
            DSLContext dsl, List<Row> rows) {
        var insert = dsl.insertInto(
                TRANSACTIONS,
                TRANSACTIONS.ID,
                TRANSACTIONS.TS,
                TRANSACTIONS.FROM_OWNER,
                TRANSACTIONS.TO_OWNER,
                TRANSACTIONS.CURRENCY,
                TRANSACTIONS.AMOUNT,
                TRANSACTIONS.TYPE,
                TRANSACTIONS.REASON);
        for (Row row : rows) {
            insert = insert.values(row.id, row.ts, row.from, row.to, row.currency, row.amount, row.type, row.reason);
        }
        return insert;
    }

    private void scheduleNext() {
        scheduler.asyncAfter(interval, () -> {
            if (!running) {
                return;
            }
            flush();
            scheduleNext();
        });
    }

    private long nextId() {
        return ids.incrementAndGet();
    }

    /** One buffered ledger row; a single-sided move leaves the absent side null, mirroring the table shape. */
    private record Row(
            long id,
            long ts,
            @Nullable String from,
            @Nullable String to,
            String currency,
            BigDecimal amount,
            String type,
            String reason) {

        static Row single(long id, long ts, PlayerRef owner, Money amount, String type, EconomyReason reason) {
            boolean credit = "CREDIT".equals(type);
            return new Row(
                    id,
                    ts,
                    credit ? null : owner.uuid().toString(),
                    credit ? owner.uuid().toString() : null,
                    amount.currency().id().value(),
                    amount.amount(),
                    type,
                    reason.name());
        }

        static Row transfer(long id, long ts, PlayerRef from, PlayerRef to, Money amount, EconomyReason reason) {
            return new Row(
                    id,
                    ts,
                    from.uuid().toString(),
                    to.uuid().toString(),
                    amount.currency().id().value(),
                    amount.amount(),
                    "TRANSFER",
                    reason.name());
        }
    }
}
