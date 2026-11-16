package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyOwners.ECONOMY_OWNERS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.Transactions.TRANSACTIONS;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.uxplima.uxmessentials.economy.application.port.HistoryRecord;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.persistence.runtime.IdBlocks;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.RowsRefused;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jooq.InsertValuesStep8;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The append-only ledger-history writer for the {@code transactions} table. Unlike the debounced settle queue
 * ({@link DebouncedWalletWriter}), telemetry rows must <strong>all</strong> survive, none may collapse, so
 * they buffer in memory and flush as one batch {@code INSERT} on a fixed interval
 * (debounce collapses, batch preserves). Each row records one
 * applied money move with its currency, type, and {@link EconomyReason} stored as their stable enum names so
 * an audit review can {@code GROUP BY} them.
 *
 * <p>This is the ledger, separate from the operator audit channel. The buffer drains synchronously on
 * {@link #stop()} so a clean shutdown never strands history. A transient batch-flush failure re-enqueues the
 * drained rows (bounded by {@link #MAX_RETAINED}) so the next window retries them rather than dropping a
 * whole telemetry window.
 *
 * <p>A row gets its id when it is flushed, from a block the database hands out ({@link IdBlocks}), so every server
 * sharing the database writes ids no other server writes. A batch the database refuses for its rows (a duplicate id,
 * a value a column cannot hold) is not a transient failure and is never requeued: the allocator is wound past every
 * id already written, which a server on an older release may have taken, and the batch tried again with fresh ids.
 * Refused again, it is halved and each half stored the same way, so only the rows no database would take are dropped
 * and logged, never the batch around them.
 */
@NullMarked
public final class TransactionTelemetry extends JooqRepository implements TransactionHistory {

    /**
     * Cap on how many rows a failed flush may re-enqueue. A persistent outage would otherwise let the buffer
     * grow without bound; past this watermark the oldest rows are dropped (logged) to protect heap, accepting
     * that an extended outage loses the deepest history rather than OOM-ing the server.
     */
    private static final int MAX_RETAINED = 100_000;

    /** How many dropped rows one log line describes; the count covers them all. */
    private static final int DROPPED_SHOWN = 5;

    /** The {@code id_allocator} sequence the ledger's ids are drawn from. */
    static final String ID_SEQUENCE = "transactions";

    private final Queue<Row> buffer = new ConcurrentLinkedQueue<>();
    private final IdBlocks ids;
    private final Scheduler scheduler;
    private final Logger log;
    private final Duration interval;
    private volatile boolean running;

    public TransactionTelemetry(DSLContext dsl, Scheduler scheduler, Logger log, Duration interval) {
        super(dsl);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.interval = Objects.requireNonNull(interval, "interval");
        this.ids = new IdBlocks(dsl, ID_SEQUENCE);
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
        buffer.add(Row.single(at, owner, amount, "CREDIT", reason));
    }

    /** Record a single-sided debit from {@code owner}. */
    public void debit(PlayerRef owner, Money amount, EconomyReason reason, long at) {
        buffer.add(Row.single(at, owner, amount, "DEBIT", reason));
    }

    /** Record an atomic transfer carrying both sides. */
    public void transfer(PlayerRef from, PlayerRef to, Money amount, EconomyReason reason, long at) {
        buffer.add(Row.transfer(at, from, to, amount, reason));
    }

    @Override
    public void recordTransfer(String fromId, String toId, Money amount, EconomyReason reason, long at) {
        buffer.add(
                new Row(at, fromId, toId, amount.currency().id().value(), amount.amount(), "TRANSFER", reason.name()));
    }

    @Override
    public void recordCredit(String ownerId, Money amount, EconomyReason reason, long at) {
        buffer.add(
                new Row(at, null, ownerId, amount.currency().id().value(), amount.amount(), "CREDIT", reason.name()));
    }

    @Override
    public void recordDebit(String ownerId, Money amount, EconomyReason reason, long at) {
        buffer.add(new Row(at, ownerId, null, amount.currency().id().value(), amount.amount(), "DEBIT", reason.name()));
    }

    @Override
    public List<HistoryRecord> queryBankTransactions(String bankId, int limit, int offset) {
        var fromOwners = ECONOMY_OWNERS.as("from_owners");
        var toOwners = ECONOMY_OWNERS.as("to_owners");
        return read(dsl -> dsl.select(
                        TRANSACTIONS.ID,
                        TRANSACTIONS.TS,
                        TRANSACTIONS.FROM_OWNER,
                        fromOwners.NAME,
                        TRANSACTIONS.TO_OWNER,
                        toOwners.NAME,
                        TRANSACTIONS.CURRENCY,
                        TRANSACTIONS.AMOUNT,
                        TRANSACTIONS.TYPE,
                        TRANSACTIONS.REASON)
                .from(TRANSACTIONS)
                .leftJoin(fromOwners)
                .on(TRANSACTIONS.FROM_OWNER.eq(fromOwners.OWNER))
                .leftJoin(toOwners)
                .on(TRANSACTIONS.TO_OWNER.eq(toOwners.OWNER))
                .where(TRANSACTIONS.FROM_OWNER.eq(bankId))
                .or(TRANSACTIONS.TO_OWNER.eq(bankId))
                .orderBy(TRANSACTIONS.TS.desc())
                .limit(limit)
                .offset(offset)
                .fetch()
                .map(record -> new HistoryRecord(
                        record.get(TRANSACTIONS.ID),
                        Instant.ofEpochMilli(record.get(TRANSACTIONS.TS)),
                        record.get(TRANSACTIONS.FROM_OWNER),
                        record.get(fromOwners.NAME),
                        record.get(TRANSACTIONS.TO_OWNER),
                        record.get(toOwners.NAME),
                        record.get(TRANSACTIONS.CURRENCY),
                        record.get(TRANSACTIONS.AMOUNT),
                        record.get(TRANSACTIONS.TYPE),
                        record.get(TRANSACTIONS.REASON))));
    }

    /**
     * Write every buffered row as one batch {@code INSERT}, with ids from one block reserved for the batch. Runs
     * off-tick on the flush loop.
     */
    @Override
    public void flush() {
        List<Row> drained = drain();
        if (drained.isEmpty()) {
            return;
        }
        Dropped dropped = new Dropped();
        store(drained, dropped);
        dropped.report(log);
    }

    /**
     * Store {@code rows}, or put them back for the next window when the failure is transient. A refusal of the rows
     * themselves winds the ids forward and tries again, then splits the rows in two, down to the single rows that
     * cannot be stored at all.
     */
    private void store(List<Row> rows, Dropped dropped) {
        RuntimeException failure = insert(rows);
        if (failure == null) {
            return;
        }
        if (!RowsRefused.causedBy(failure)) {
            requeue(rows);
            return;
        }
        try {
            windIdsForward();
        } catch (RuntimeException windFailed) {
            requeue(rows);
            return;
        }
        failure = insert(rows);
        if (failure == null) {
            return;
        }
        if (!RowsRefused.causedBy(failure)) {
            requeue(rows);
            return;
        }
        if (rows.size() == 1) {
            dropped.add(rows.getFirst(), failure);
            return;
        }
        int half = rows.size() / 2;
        store(rows.subList(0, half), dropped);
        store(rows.subList(half, rows.size()), dropped);
    }

    /** Insert {@code rows} under a fresh block of ids; the failure, or null once they are stored. */
    private @Nullable RuntimeException insert(List<Row> rows) {
        try {
            long first = ids.reserve(rows.size());
            write(dsl -> {
                batch(dsl, rows, first).execute();
                return null;
            });
            return null;
        } catch (RuntimeException failure) {
            return failure;
        }
    }

    /**
     * Reserve {@code count} ledger ids for rows written outside this buffer and return the first. Like every
     * reservation it runs in a transaction of its own, so a caller reserves before it opens the transaction that
     * writes the rows.
     */
    public long reserveIds(int count) {
        return ids.reserve(count);
    }

    /**
     * Move the ledger's id sequence past every id already written, for a writer outside this buffer whose rows met an
     * id a server on an older release took. Runs in a transaction of its own.
     */
    public void windIdsForward() {
        ids.windPast(TRANSACTIONS, TRANSACTIONS.ID);
    }

    /**
     * Put the drained rows back so the next window retries them. A transient DB blip must not silently lose a
     * telemetry window (these rows are the audit ledger). Bounded by {@link #MAX_RETAINED}: once the buffer is
     * at the watermark the overflow is dropped and logged, trading the deepest history for heap safety under a
     * sustained outage. Re-add is to the tail; rows carry their own {@code ts}, and queries order by it.
     */
    private void requeue(List<Row> drained) {
        int dropped = 0;
        for (Row row : drained) {
            if (buffer.size() >= MAX_RETAINED) {
                dropped++;
                continue;
            }
            buffer.add(row);
        }
        if (dropped > 0) {
            log.warn(
                    "transaction telemetry batch failed to flush; re-enqueued {} rows, dropped {} over the {} cap",
                    drained.size() - dropped,
                    dropped,
                    MAX_RETAINED);
        } else {
            log.warn(
                    "transaction telemetry batch failed to flush; re-enqueued {} rows for the next window",
                    drained.size());
        }
    }

    @Override
    public List<HistoryRecord> queryTransactions(UUID playerUuid, int limit, int offset) {
        String uuidStr = playerUuid.toString();
        var fromOwners = ECONOMY_OWNERS.as("from_owners");
        var toOwners = ECONOMY_OWNERS.as("to_owners");
        return read(dsl -> dsl.select(
                        TRANSACTIONS.ID,
                        TRANSACTIONS.TS,
                        TRANSACTIONS.FROM_OWNER,
                        fromOwners.NAME,
                        TRANSACTIONS.TO_OWNER,
                        toOwners.NAME,
                        TRANSACTIONS.CURRENCY,
                        TRANSACTIONS.AMOUNT,
                        TRANSACTIONS.TYPE,
                        TRANSACTIONS.REASON)
                .from(TRANSACTIONS)
                .leftJoin(fromOwners)
                .on(TRANSACTIONS.FROM_OWNER.eq(fromOwners.OWNER))
                .leftJoin(toOwners)
                .on(TRANSACTIONS.TO_OWNER.eq(toOwners.OWNER))
                .where(TRANSACTIONS.FROM_OWNER.eq(uuidStr))
                .or(TRANSACTIONS.TO_OWNER.eq(uuidStr))
                .orderBy(TRANSACTIONS.TS.desc())
                .limit(limit)
                .offset(offset)
                .fetch()
                .map(record -> new HistoryRecord(
                        record.get(TRANSACTIONS.ID),
                        Instant.ofEpochMilli(record.get(TRANSACTIONS.TS)),
                        record.get(TRANSACTIONS.FROM_OWNER),
                        record.get(fromOwners.NAME),
                        record.get(TRANSACTIONS.TO_OWNER),
                        record.get(toOwners.NAME),
                        record.get(TRANSACTIONS.CURRENCY),
                        record.get(TRANSACTIONS.AMOUNT),
                        record.get(TRANSACTIONS.TYPE),
                        record.get(TRANSACTIONS.REASON))));
    }

    @Override
    public List<HistoryRecord> queryGlobalTransactions(int limit, int offset) {
        var fromOwners = ECONOMY_OWNERS.as("from_owners");
        var toOwners = ECONOMY_OWNERS.as("to_owners");
        return read(dsl -> dsl.select(
                        TRANSACTIONS.ID,
                        TRANSACTIONS.TS,
                        TRANSACTIONS.FROM_OWNER,
                        fromOwners.NAME,
                        TRANSACTIONS.TO_OWNER,
                        toOwners.NAME,
                        TRANSACTIONS.CURRENCY,
                        TRANSACTIONS.AMOUNT,
                        TRANSACTIONS.TYPE,
                        TRANSACTIONS.REASON)
                .from(TRANSACTIONS)
                .leftJoin(fromOwners)
                .on(TRANSACTIONS.FROM_OWNER.eq(fromOwners.OWNER))
                .leftJoin(toOwners)
                .on(TRANSACTIONS.TO_OWNER.eq(toOwners.OWNER))
                .orderBy(TRANSACTIONS.TS.desc())
                .limit(limit)
                .offset(offset)
                .fetch()
                .map(record -> new HistoryRecord(
                        record.get(TRANSACTIONS.ID),
                        Instant.ofEpochMilli(record.get(TRANSACTIONS.TS)),
                        record.get(TRANSACTIONS.FROM_OWNER),
                        record.get(fromOwners.NAME),
                        record.get(TRANSACTIONS.TO_OWNER),
                        record.get(toOwners.NAME),
                        record.get(TRANSACTIONS.CURRENCY),
                        record.get(TRANSACTIONS.AMOUNT),
                        record.get(TRANSACTIONS.TYPE),
                        record.get(TRANSACTIONS.REASON))));
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

    private static InsertValuesStep8<?, Long, Long, String, String, String, BigDecimal, String, String> batch(
            DSLContext dsl, List<Row> rows, long firstId) {
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
        long id = firstId;
        for (Row row : rows) {
            insert = insert.values(id++, row.ts, row.from, row.to, row.currency, row.amount, row.type, row.reason);
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

    /** The rows of one flush no database would store, told to the operator in one line. */
    private static final class Dropped {

        private final List<Row> rows = new ArrayList<>();
        private @Nullable RuntimeException first;

        void add(Row row, RuntimeException failure) {
            rows.add(row);
            if (first == null) {
                first = failure;
            }
        }

        void report(Logger log) {
            RuntimeException cause = first;
            if (cause == null) {
                return;
            }
            log.error(
                    "event=ledger_rows_dropped rows=" + rows.size() + " shown="
                            + rows.subList(0, Math.min(DROPPED_SHOWN, rows.size()))
                            + " (the database refused these history rows; balances are not affected)",
                    cause);
        }
    }

    /**
     * One buffered ledger row; a single-sided move leaves the absent side null, mirroring the table shape. Its id is
     * given at flush time.
     */
    private record Row(
            long ts,
            @Nullable String from,
            @Nullable String to,
            String currency,
            BigDecimal amount,
            String type,
            String reason) {

        static Row single(long ts, PlayerRef owner, Money amount, String type, EconomyReason reason) {
            boolean credit = "CREDIT".equals(type);
            return new Row(
                    ts,
                    credit ? null : owner.uuid().toString(),
                    credit ? owner.uuid().toString() : null,
                    amount.currency().id().value(),
                    amount.amount(),
                    type,
                    reason.name());
        }

        static Row transfer(long ts, PlayerRef from, PlayerRef to, Money amount, EconomyReason reason) {
            return new Row(
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
