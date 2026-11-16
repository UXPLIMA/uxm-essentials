package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyOwners.ECONOMY_OWNERS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.Transactions.TRANSACTIONS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.WalletBalances.WALLET_BALANCES;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntToLongFunction;

import com.uxplima.uxmessentials.economy.application.NativeCurrencyBackend;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.SQLDialect;
import org.jooq.SelectConditionStep;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.SQLStateClass;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link AccountSweep} over the native ledger's tables. A sweep walks {@code economy_owners} in owner order, a
 * chunk at a time, and for each chunk locks the chunk's balance rows, decides from the locked figures which accounts
 * change, writes them with one {@code UPDATE} and writes their ledger rows with one {@code INSERT}.
 *
 * <p>The whole walk is one transaction. A per-row loop of credits would leave a sweep cut short by a stop or a lost
 * connection half applied, and running the command again would pay the first half twice; a transaction per chunk has
 * the same flaw, only coarser, and would need a journal to resume. One transaction leaves nothing to resume: either
 * every account changed and has its ledger row, or the database rolled all of it back. The price is that the rows of
 * one currency stay locked until the sweep commits, which on a ledger of a hundred thousand accounts is seconds; a
 * payment that touches a locked row waits that long and then lands on the new figure.
 *
 * <p>Every figure the sweep writes is decided from the row as it is locked, so a payment that commits first is part
 * of what the sweep sees, and one that commits after applies its own change on top. A credit is written as an
 * increment, never as a recomputed total, so it cannot overwrite a concurrent payment. An account that never held the
 * currency first gets a zero row (which reads exactly like no row), so it is locked and written like any other.
 *
 * <p>SQLite has no {@code FOR UPDATE}; it locks the whole file for a writer instead. The sweep takes that write lock
 * with its first statement, before it reads anything, so no other connection can commit between a figure the sweep
 * read and the write it decides on.
 */
@NullMarked
public final class JooqAccountSweep implements AccountSweep {

    /** Owners per chunk: keeps every statement well under the bind-parameter limit of each backend. */
    static final int CHUNK = 500;

    /** Runs of a sweep the database may roll back to break a lock cycle before the failure is passed on. */
    private static final int ATTEMPTS = 3;

    private static final String CREDIT = "CREDIT";
    private static final String DEBIT = "DEBIT";

    private final DSLContext dsl;
    private final IntToLongFunction ledgerIds;

    /**
     * @param ledgerIds hands out the given number of consecutive ledger ids and returns the first, from the same
     *     sequence the buffered ledger writer draws from
     */
    public JooqAccountSweep(DSLContext dsl, IntToLongFunction ledgerIds) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
        this.ledgerIds = Objects.requireNonNull(ledgerIds, "ledgerIds");
    }

    @Override
    public boolean reaches(Currency currency) {
        Objects.requireNonNull(currency, "currency");
        // A physical currency's balance is the items in an inventory, and a foreign backend's is another plugin's.
        return NativeCurrencyBackend.ID.equals(currency.backendId()) && !currency.isPhysical();
    }

    @Override
    public SweepOutcome creditEvery(Money amount, EconomyReason reason, long at) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
        if (amount.amount().signum() <= 0) {
            throw new IllegalArgumentException("a sweep credits a positive amount: " + amount.amount());
        }
        requireReached(amount.currency());
        return walk(chunk -> creditChunk(chunk, amount, reason, at));
    }

    @Override
    public SweepOutcome resetEvery(Money balance, EconomyReason reason, long at) {
        Objects.requireNonNull(balance, "balance");
        Objects.requireNonNull(reason, "reason");
        requireReached(balance.currency());
        return walk(chunk -> resetChunk(chunk, balance, reason, at));
    }

    private void requireReached(Currency currency) {
        if (!reaches(currency)) {
            throw new IllegalArgumentException(
                    "currency " + currency.id().value() + " is not kept in this ledger's rows");
        }
    }

    /**
     * Run {@code work} over every owner, a chunk at a time, all in one transaction, and add up what it did.
     *
     * <p>A payment locks its two rows in its own order, so it and the sweep can each wait on the other. The database
     * then rolls one of them back with a transaction-rollback state. When that is the sweep, nothing of it was
     * committed, so running it again from the start is safe; any other failure is passed on.
     */
    private SweepOutcome walk(ChunkWork work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return dsl.transactionResult(configuration -> walkWithin(configuration.dsl(), work));
            } catch (DataAccessException failure) {
                if (attempt >= ATTEMPTS || failure.sqlStateClass() != SQLStateClass.C40_TRANSACTION_ROLLBACK) {
                    throw new PersistenceException("sweep failed and was rolled back", failure);
                }
            }
        }
    }

    private static SweepOutcome walkWithin(DSLContext tx, ChunkWork work) {
        if (sqlite(tx)) {
            // A write that matches no row still makes this transaction SQLite's one writer, waiting for the lock.
            tx.update(ECONOMY_OWNERS)
                    .set(ECONOMY_OWNERS.OWNER, ECONOMY_OWNERS.OWNER)
                    .where(DSL.falseCondition())
                    .execute();
        }
        SweepOutcome total = SweepOutcome.none();
        String after = "";
        while (true) {
            List<String> owners = tx.select(ECONOMY_OWNERS.OWNER)
                    .from(ECONOMY_OWNERS)
                    .where(ECONOMY_OWNERS.OWNER.gt(after))
                    .orderBy(ECONOMY_OWNERS.OWNER)
                    .limit(CHUNK)
                    .fetch(ECONOMY_OWNERS.OWNER);
            if (owners.isEmpty()) {
                return total;
            }
            total = total.plus(work.apply(new Chunk(tx, owners)));
            after = owners.getLast();
        }
    }

    private SweepOutcome creditChunk(Chunk chunk, Money amount, EconomyReason reason, long at) {
        Currency currency = amount.currency();
        BigDecimal ceiling = currency.max().subtract(amount.amount());
        if (ceiling.signum() < 0) {
            // More than the currency can hold at all: every account would break the limit, so none is touched.
            return new SweepOutcome(0, chunk.owners().size());
        }
        openMissing(chunk, currency);
        Map<String, BigDecimal> locked = lockedBalances(chunk, currency);
        List<String> credited = new ArrayList<>();
        for (String owner : chunk.owners()) {
            BigDecimal before = locked.get(owner);
            if (before != null && before.compareTo(ceiling) <= 0) {
                credited.add(owner);
            }
        }
        if (credited.isEmpty()) {
            return new SweepOutcome(0, chunk.owners().size());
        }
        int updated = chunk.tx()
                .update(WALLET_BALANCES)
                .set(WALLET_BALANCES.AMOUNT, WALLET_BALANCES.AMOUNT.plus(amount.amount()))
                .where(WALLET_BALANCES.CURRENCY.eq(currency.id().value()))
                .and(WALLET_BALANCES.OWNER.in(credited))
                .execute();
        requireAll(updated, credited.size());
        List<LedgerRow> rows = new ArrayList<>(credited.size());
        for (String owner : credited) {
            rows.add(new LedgerRow(owner, amount.amount(), CREDIT));
        }
        writeLedger(chunk.tx(), rows, currency, reason, at);
        return new SweepOutcome(credited.size(), chunk.owners().size() - credited.size());
    }

    private SweepOutcome resetChunk(Chunk chunk, Money balance, EconomyReason reason, long at) {
        Currency currency = balance.currency();
        BigDecimal target = balance.amount();
        if (target.signum() != 0) {
            // An account with no row holds zero; it must get a row to hold anything else.
            openMissing(chunk, currency);
        }
        Map<String, BigDecimal> locked = lockedBalances(chunk, currency);
        List<String> changed = new ArrayList<>();
        List<LedgerRow> rows = new ArrayList<>();
        for (String owner : chunk.owners()) {
            BigDecimal before = locked.get(owner);
            if (before == null || before.compareTo(target) == 0) {
                // No row with a zero target, or already there: the account holds the target and is not changed.
                continue;
            }
            changed.add(owner);
            BigDecimal delta = target.subtract(before);
            rows.add(new LedgerRow(owner, delta.abs(), delta.signum() > 0 ? CREDIT : DEBIT));
        }
        if (changed.isEmpty()) {
            return SweepOutcome.none();
        }
        int updated = chunk.tx()
                .update(WALLET_BALANCES)
                .set(WALLET_BALANCES.AMOUNT, target)
                .where(WALLET_BALANCES.CURRENCY.eq(currency.id().value()))
                .and(WALLET_BALANCES.OWNER.in(changed))
                .execute();
        requireAll(updated, changed.size());
        writeLedger(chunk.tx(), rows, currency, reason, at);
        return new SweepOutcome(changed.size(), 0);
    }

    /**
     * Give every owner of the chunk without a balance in {@code currency} a zero one, which reads exactly as no row
     * does, so the next statement can lock it. A row a concurrent first payment opens in between is left as it is.
     */
    private static void openMissing(Chunk chunk, Currency currency) {
        String currencyId = currency.id().value();
        Set<String> held = new HashSet<>(chunk.tx()
                .select(WALLET_BALANCES.OWNER)
                .from(WALLET_BALANCES)
                .where(WALLET_BALANCES.CURRENCY.eq(currencyId))
                .and(WALLET_BALANCES.OWNER.in(chunk.owners()))
                .fetch(WALLET_BALANCES.OWNER));
        var insert = chunk.tx()
                .insertInto(WALLET_BALANCES, WALLET_BALANCES.OWNER, WALLET_BALANCES.CURRENCY, WALLET_BALANCES.AMOUNT);
        boolean any = false;
        for (String owner : chunk.owners()) {
            if (!held.contains(owner)) {
                insert = insert.values(owner, currencyId, BigDecimal.ZERO);
                any = true;
            }
        }
        if (any) {
            insert.onConflictDoNothing().execute();
        }
    }

    /** The chunk's balances in {@code currency}, read under a row lock where the backend has one. */
    private static Map<String, BigDecimal> lockedBalances(Chunk chunk, Currency currency) {
        SelectConditionStep<Record2<String, BigDecimal>> select = chunk.tx()
                .select(WALLET_BALANCES.OWNER, WALLET_BALANCES.AMOUNT)
                .from(WALLET_BALANCES)
                .where(WALLET_BALANCES.CURRENCY.eq(currency.id().value()))
                .and(WALLET_BALANCES.OWNER.in(chunk.owners()));
        List<Record2<String, BigDecimal>> rows =
                sqlite(chunk.tx()) ? select.fetch() : select.forUpdate().fetch();
        Map<String, BigDecimal> balances = new HashMap<>();
        for (Record2<String, BigDecimal> row : rows) {
            balances.put(row.value1(), row.value2());
        }
        return balances;
    }

    /**
     * The update must have written exactly the rows decided on, or the ledger rows about to be written would not
     * match the balances; failing rolls the whole sweep back instead.
     */
    private static void requireAll(int updated, int expected) {
        if (updated != expected) {
            throw new IllegalStateException(
                    "sweep updated " + updated + " balances but decided on " + expected + "; rolled back");
        }
    }

    private void writeLedger(DSLContext tx, List<LedgerRow> rows, Currency currency, EconomyReason reason, long at) {
        long id = ledgerIds.applyAsLong(rows.size());
        var insert = tx.insertInto(
                TRANSACTIONS,
                TRANSACTIONS.ID,
                TRANSACTIONS.TS,
                TRANSACTIONS.FROM_OWNER,
                TRANSACTIONS.TO_OWNER,
                TRANSACTIONS.CURRENCY,
                TRANSACTIONS.AMOUNT,
                TRANSACTIONS.TYPE,
                TRANSACTIONS.REASON);
        for (LedgerRow row : rows) {
            boolean credit = CREDIT.equals(row.type());
            @Nullable String from = credit ? null : row.owner();
            @Nullable String to = credit ? row.owner() : null;
            insert = insert.values(id++, at, from, to, currency.id().value(), row.amount(), row.type(), reason.name());
        }
        insert.execute();
    }

    private static boolean sqlite(DSLContext tx) {
        return tx.dialect().family() == SQLDialect.SQLITE;
    }

    /** One chunk of owners and the transaction the whole sweep runs in. */
    private record Chunk(DSLContext tx, List<String> owners) {}

    /** One account's ledger row: a single-sided credit or debit, shaped like the buffered writer's. */
    private record LedgerRow(String owner, BigDecimal amount, String type) {}

    @FunctionalInterface
    private interface ChunkWork {
        SweepOutcome apply(Chunk chunk);
    }
}
