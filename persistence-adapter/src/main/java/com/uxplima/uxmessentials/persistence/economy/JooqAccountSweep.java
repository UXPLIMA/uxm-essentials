package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyOwners.ECONOMY_OWNERS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomySweeps.ECONOMY_SWEEPS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerNames.PLAYER_NAMES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.Transactions.TRANSACTIONS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.WalletBalances.WALLET_BALANCES;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntToLongFunction;

import com.uxplima.uxmessentials.economy.application.NativeCurrencyBackend;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SweepKind;
import com.uxplima.uxmessentials.economy.domain.SweepScope;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.EconomySweepsRecord;
import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import com.uxplima.uxmessentials.persistence.runtime.Transactions;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.SQLDialect;
import org.jooq.SelectConditionStep;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.SQLStateClass;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link AccountSweep} over the native ledger's tables, journalled in {@code economy_sweeps}.
 *
 * <p>Each {@link #advance} is one short transaction over the next {@link #CHUNK} owners after the journal's cursor:
 * lock the journal row, list the owners, lock them (an owner a wallet purge removed in between is counted as gone),
 * give each owner without a balance a zero one (it reads exactly like no row), lock the chunk's balances, decide from
 * the locked figures, write them with one {@code UPDATE}, write their ledger rows with one {@code INSERT}, and move the
 * cursor. Locks are held for one chunk, milliseconds, so a payment or a Vault call on a server thread barely waits.
 * Because the cursor moves with the balances, a sweep that stops between chunks resumes exactly where it stopped,
 * and two runners of one sweep queue on the journal row rather than repeat a chunk.
 *
 * <p>A credit is written as an increment, never as a recomputed total, so it cannot overwrite a concurrent payment;
 * a reset's ledger delta is taken from the locked row. When the database rolls a chunk back to break a lock cycle
 * (SQLSTATE class 40) the chunk is run again, which is safe because nothing of it committed.
 *
 * <p>SQLite has no {@code FOR UPDATE}; it locks the whole file for a writer instead. A chunk takes that write lock
 * with its first statement, before it reads anything, so no other connection commits between a figure it read and
 * the write it decides on.
 */
@NullMarked
public final class JooqAccountSweep implements AccountSweep {

    /** Owners per chunk: small enough that a chunk's locks last milliseconds, large enough to keep round trips few. */
    static final int CHUNK = 200;

    /** Runs of a chunk the database may roll back to break a lock cycle before the failure is passed on. */
    private static final int ATTEMPTS = 3;

    private static final String CREDIT = "CREDIT";
    private static final String DEBIT = "DEBIT";
    private static final String RUNNING = "RUNNING";
    private static final String DONE = "DONE";

    private final DSLContext dsl;
    private final IntToLongFunction ledgerIds;
    private final CurrencyRegistry currencies;
    private final Clock clock;
    private final Logger log;

    /**
     * @param ledgerIds hands out the given number of consecutive ledger ids and returns the first, from the same
     *     sequence the buffered ledger writer draws from; it runs in a transaction of its own, so it is called before
     *     a chunk's transaction opens
     */
    public JooqAccountSweep(
            DSLContext dsl, IntToLongFunction ledgerIds, CurrencyRegistry currencies, Clock clock, Logger log) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
        this.ledgerIds = Objects.requireNonNull(ledgerIds, "ledgerIds");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean reaches(Currency currency) {
        Objects.requireNonNull(currency, "currency");
        // A physical currency's balance is the items in an inventory, and a foreign backend's is another plugin's.
        return NativeCurrencyBackend.ID.equals(currency.backendId()) && !currency.isPhysical();
    }

    @Override
    public Sweep open(SweepKind kind, SweepScope scope, Money amount, EconomyReason reason, PlayerRef actor, long at) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(actor, "actor");
        if (!reaches(amount.currency())) {
            throw new IllegalArgumentException(
                    "currency " + amount.currency().id().value() + " is not kept in this ledger's rows");
        }
        if (kind == SweepKind.CREDIT && amount.amount().signum() <= 0) {
            throw new IllegalArgumentException("a sweep credits a positive amount: " + amount.amount());
        }
        Sweep sweep = new Sweep(UUID.randomUUID().toString(), kind, scope, amount, reason, actor, at);
        Transactions.inTransaction(
                dsl,
                tx -> tx.insertInto(ECONOMY_SWEEPS)
                        .set(ECONOMY_SWEEPS.ID, sweep.id())
                        .set(ECONOMY_SWEEPS.KIND, kind.name())
                        .set(ECONOMY_SWEEPS.SCOPE, scope.name())
                        .set(ECONOMY_SWEEPS.CURRENCY, amount.currency().id().value())
                        .set(ECONOMY_SWEEPS.AMOUNT, amount.amount())
                        .set(ECONOMY_SWEEPS.REASON, reason.name())
                        .set(ECONOMY_SWEEPS.ACTOR, actor.uuid().toString())
                        .set(ECONOMY_SWEEPS.ACTOR_NAME, shortName(actor.name()))
                        .set(ECONOMY_SWEEPS.STARTED_AT, at)
                        .set(ECONOMY_SWEEPS.AFTER_OWNER, "")
                        .set(ECONOMY_SWEEPS.CHANGED, 0)
                        .set(ECONOMY_SWEEPS.SKIPPED, 0)
                        .set(ECONOMY_SWEEPS.GONE, 0)
                        .set(ECONOMY_SWEEPS.STATE, RUNNING)
                        .execute());
        return sweep;
    }

    @Override
    public Step advance(Sweep sweep) {
        Objects.requireNonNull(sweep, "sweep");
        long firstId = ledgerIds.applyAsLong(CHUNK);
        for (int attempt = 1; ; attempt++) {
            try {
                return dsl.transactionResult(configuration -> chunk(configuration.dsl(), sweep, firstId));
            } catch (DataAccessException failure) {
                if (attempt >= ATTEMPTS || failure.sqlStateClass() != SQLStateClass.C40_TRANSACTION_ROLLBACK) {
                    throw new PersistenceException("sweep chunk failed and was rolled back", failure);
                }
            }
        }
    }

    @Override
    public List<Sweep> unfinished() {
        List<EconomySweepsRecord> rows = Transactions.inTransaction(
                dsl,
                tx -> tx.selectFrom(ECONOMY_SWEEPS)
                        .where(ECONOMY_SWEEPS.STATE.eq(RUNNING))
                        .orderBy(ECONOMY_SWEEPS.STARTED_AT, ECONOMY_SWEEPS.ID)
                        .fetch());
        List<Sweep> sweeps = new ArrayList<>(rows.size());
        for (EconomySweepsRecord row : rows) {
            Optional<Currency> currency = currencies.find(CurrencyId.of(row.getCurrency()));
            if (currency.isEmpty()) {
                // Left running: it carries on once the currency is configured again.
                log.warn("event=economy_sweep_currency_unknown id={} currency={}", row.getId(), row.getCurrency());
                continue;
            }
            sweeps.add(new Sweep(
                    row.getId(),
                    SweepKind.valueOf(row.getKind()),
                    SweepScope.valueOf(row.getScope()),
                    Money.of(currency.get(), row.getAmount()),
                    EconomyReason.valueOf(row.getReason()),
                    new PlayerRef(UUID.fromString(row.getActor()), row.getActorName()),
                    row.getStartedAt()));
        }
        return sweeps;
    }

    /** One chunk of {@code sweep}, inside the transaction {@code tx}. */
    private Step chunk(DSLContext tx, Sweep sweep, long firstId) {
        if (sqlite(tx)) {
            // A write that matches no row still makes this transaction SQLite's one writer, waiting for the lock.
            tx.update(ECONOMY_SWEEPS)
                    .set(ECONOMY_SWEEPS.STATE, ECONOMY_SWEEPS.STATE)
                    .where(DSL.falseCondition())
                    .execute();
        }
        EconomySweepsRecord journal = lockJournal(tx, sweep.id());
        SweepOutcome total = new SweepOutcome(journal.getChanged(), journal.getSkipped(), journal.getGone());
        if (DONE.equals(journal.getState())) {
            return new Step(total, List.of(), Progress.ALREADY_FINISHED);
        }
        List<String> listed = nextOwners(tx, sweep.scope(), journal.getAfterOwner());
        if (listed.isEmpty()) {
            tx.update(ECONOMY_SWEEPS)
                    .set(ECONOMY_SWEEPS.STATE, DONE)
                    .set(ECONOMY_SWEEPS.FINISHED_AT, clock.millis())
                    .where(ECONOMY_SWEEPS.ID.eq(sweep.id()))
                    .execute();
            return new Step(total, List.of(), Progress.FINISHED);
        }
        List<String> owners = lockOwners(tx, listed);
        ChunkResult result = sweep.kind() == SweepKind.CREDIT
                ? credit(tx, owners, sweep.amount())
                : reset(tx, owners, sweep.amount());
        writeLedger(tx, result.rows(), sweep, firstId);
        SweepOutcome done = new SweepOutcome(result.rows().size(), result.skipped(), listed.size() - owners.size());
        int moved = tx.update(ECONOMY_SWEEPS)
                .set(ECONOMY_SWEEPS.AFTER_OWNER, listed.getLast())
                .set(ECONOMY_SWEEPS.CHANGED, ECONOMY_SWEEPS.CHANGED.plus(done.changed()))
                .set(ECONOMY_SWEEPS.SKIPPED, ECONOMY_SWEEPS.SKIPPED.plus(done.skipped()))
                .set(ECONOMY_SWEEPS.GONE, ECONOMY_SWEEPS.GONE.plus(done.gone()))
                .where(ECONOMY_SWEEPS.ID.eq(sweep.id()))
                .and(ECONOMY_SWEEPS.AFTER_OWNER.eq(journal.getAfterOwner()))
                .execute();
        requireAll(moved, 1);
        List<UUID> changed = new ArrayList<>(result.rows().size());
        for (LedgerRow row : result.rows()) {
            changed.add(UUID.fromString(row.owner()));
        }
        return new Step(total.plus(done), changed, Progress.MORE);
    }

    private static EconomySweepsRecord lockJournal(DSLContext tx, String id) {
        SelectConditionStep<EconomySweepsRecord> select =
                tx.selectFrom(ECONOMY_SWEEPS).where(ECONOMY_SWEEPS.ID.eq(id));
        @Nullable EconomySweepsRecord row =
                sqlite(tx) ? select.fetchOne() : select.forUpdate().fetchOne();
        if (row == null) {
            throw new IllegalStateException("no sweep " + id + " in the journal");
        }
        return row;
    }

    /** The next owners after {@code after} in owner order; for {@link SweepScope#PLAYERS} only players who joined. */
    private static List<String> nextOwners(DSLContext tx, SweepScope scope, String after) {
        SelectConditionStep<Record1<String>> select =
                tx.select(ECONOMY_OWNERS.OWNER).from(ECONOMY_OWNERS).where(ECONOMY_OWNERS.OWNER.gt(after));
        if (scope == SweepScope.PLAYERS) {
            // A player is an owner who has joined a server of this database; an account another plugin opened
            // through Vault for a town or a server account never has.
            select = select.and(
                    DSL.exists(DSL.selectOne().from(PLAYER_NAMES).where(PLAYER_NAMES.UUID.eq(ECONOMY_OWNERS.OWNER))));
        }
        return select.orderBy(ECONOMY_OWNERS.OWNER).limit(CHUNK).fetch(ECONOMY_OWNERS.OWNER);
    }

    /**
     * Lock the listed owners against a wallet purge for the rest of the chunk and keep, in order, those still there.
     * An owner purged since it was listed is gone; writing it a balance would break the foreign key.
     */
    private static List<String> lockOwners(DSLContext tx, List<String> listed) {
        SelectConditionStep<Record1<String>> select =
                tx.select(ECONOMY_OWNERS.OWNER).from(ECONOMY_OWNERS).where(ECONOMY_OWNERS.OWNER.in(listed));
        Set<String> present = new HashSet<>(
                sqlite(tx)
                        ? select.fetch(ECONOMY_OWNERS.OWNER)
                        : select.forShare().fetch(ECONOMY_OWNERS.OWNER));
        List<String> owners = new ArrayList<>(present.size());
        for (String owner : listed) {
            if (present.contains(owner)) {
                owners.add(owner);
            }
        }
        return owners;
    }

    private static ChunkResult credit(DSLContext tx, List<String> owners, Money amount) {
        Currency currency = amount.currency();
        BigDecimal ceiling = currency.max().subtract(amount.amount());
        if (owners.isEmpty() || ceiling.signum() < 0) {
            // More than the currency can hold at all: every account would break the limit, so none is touched.
            return new ChunkResult(List.of(), owners.size());
        }
        openMissing(tx, owners, currency);
        Map<String, BigDecimal> locked = lockedBalances(tx, owners, currency);
        List<String> credited = new ArrayList<>();
        for (String owner : owners) {
            BigDecimal before = locked.get(owner);
            if (before != null && before.compareTo(ceiling) <= 0) {
                credited.add(owner);
            }
        }
        if (credited.isEmpty()) {
            return new ChunkResult(List.of(), owners.size());
        }
        int updated = tx.update(WALLET_BALANCES)
                .set(WALLET_BALANCES.AMOUNT, WALLET_BALANCES.AMOUNT.plus(amount.amount()))
                .where(WALLET_BALANCES.CURRENCY.eq(currency.id().value()))
                .and(WALLET_BALANCES.OWNER.in(credited))
                .execute();
        requireAll(updated, credited.size());
        List<LedgerRow> rows = new ArrayList<>(credited.size());
        for (String owner : credited) {
            rows.add(new LedgerRow(owner, amount.amount(), CREDIT));
        }
        return new ChunkResult(rows, owners.size() - credited.size());
    }

    private static ChunkResult reset(DSLContext tx, List<String> owners, Money balance) {
        Currency currency = balance.currency();
        BigDecimal target = balance.amount();
        if (owners.isEmpty()) {
            return new ChunkResult(List.of(), 0);
        }
        if (target.signum() != 0) {
            // An account with no row holds zero; it must get a row to hold anything else.
            openMissing(tx, owners, currency);
        }
        Map<String, BigDecimal> locked = lockedBalances(tx, owners, currency);
        List<String> changed = new ArrayList<>();
        List<LedgerRow> rows = new ArrayList<>();
        for (String owner : owners) {
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
            return new ChunkResult(List.of(), 0);
        }
        int updated = tx.update(WALLET_BALANCES)
                .set(WALLET_BALANCES.AMOUNT, target)
                .where(WALLET_BALANCES.CURRENCY.eq(currency.id().value()))
                .and(WALLET_BALANCES.OWNER.in(changed))
                .execute();
        requireAll(updated, changed.size());
        return new ChunkResult(rows, 0);
    }

    /**
     * Give every owner without a balance in {@code currency} a zero one, which reads exactly as no row does, so the
     * next statement can lock it. A row a concurrent first payment opens in between is left as it is.
     */
    private static void openMissing(DSLContext tx, List<String> owners, Currency currency) {
        String currencyId = currency.id().value();
        Set<String> held = new HashSet<>(tx.select(WALLET_BALANCES.OWNER)
                .from(WALLET_BALANCES)
                .where(WALLET_BALANCES.CURRENCY.eq(currencyId))
                .and(WALLET_BALANCES.OWNER.in(owners))
                .fetch(WALLET_BALANCES.OWNER));
        var insert =
                tx.insertInto(WALLET_BALANCES, WALLET_BALANCES.OWNER, WALLET_BALANCES.CURRENCY, WALLET_BALANCES.AMOUNT);
        boolean any = false;
        for (String owner : owners) {
            if (!held.contains(owner)) {
                insert = insert.values(owner, currencyId, BigDecimal.ZERO);
                any = true;
            }
        }
        if (any) {
            insert.onConflictDoNothing().execute();
        }
    }

    /** The owners' balances in {@code currency}, read under a row lock where the backend has one. */
    private static Map<String, BigDecimal> lockedBalances(DSLContext tx, List<String> owners, Currency currency) {
        SelectConditionStep<Record2<String, BigDecimal>> select = tx.select(
                        WALLET_BALANCES.OWNER, WALLET_BALANCES.AMOUNT)
                .from(WALLET_BALANCES)
                .where(WALLET_BALANCES.CURRENCY.eq(currency.id().value()))
                .and(WALLET_BALANCES.OWNER.in(owners));
        List<Record2<String, BigDecimal>> rows =
                sqlite(tx) ? select.fetch() : select.forUpdate().fetch();
        Map<String, BigDecimal> balances = new HashMap<>();
        for (Record2<String, BigDecimal> row : rows) {
            balances.put(row.value1(), row.value2());
        }
        return balances;
    }

    /**
     * A write must have touched exactly the rows decided on, or the ledger rows and the journal would not match the
     * balances; failing rolls the chunk back instead.
     */
    private static void requireAll(int updated, int expected) {
        if (updated != expected) {
            throw new IllegalStateException(
                    "sweep chunk wrote " + updated + " rows but decided on " + expected + "; rolled back");
        }
    }

    private static void writeLedger(DSLContext tx, List<LedgerRow> rows, Sweep sweep, long firstId) {
        if (rows.isEmpty()) {
            return;
        }
        String currencyId = sweep.amount().currency().id().value();
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
        long id = firstId;
        for (LedgerRow row : rows) {
            boolean credit = CREDIT.equals(row.type());
            @Nullable String from = credit ? null : row.owner();
            @Nullable String to = credit ? row.owner() : null;
            insert = insert.values(
                    id++,
                    sweep.startedAt(),
                    from,
                    to,
                    currencyId,
                    row.amount(),
                    row.type(),
                    sweep.reason().name());
        }
        insert.execute();
    }

    private static boolean sqlite(DSLContext tx) {
        return tx.dialect().family() == SQLDialect.SQLITE;
    }

    private static String shortName(String name) {
        return name.length() <= 16 ? name : name.substring(0, 16);
    }

    /** What one chunk decided: a ledger row per changed account, and how many it left out for the currency's limit. */
    private record ChunkResult(List<LedgerRow> rows, int skipped) {}

    /** One account's ledger row: a single-sided credit or debit, shaped like the buffered writer's. */
    private record LedgerRow(String owner, BigDecimal amount, String type) {}
}
