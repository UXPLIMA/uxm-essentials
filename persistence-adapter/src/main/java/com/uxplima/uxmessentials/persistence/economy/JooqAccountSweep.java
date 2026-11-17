package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyItemDeliveries.ECONOMY_ITEM_DELIVERIES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyOwners.ECONOMY_OWNERS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyPendingPhysicalTransactions.ECONOMY_PENDING_PHYSICAL_TRANSACTIONS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomySweeps.ECONOMY_SWEEPS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerNames.PLAYER_NAMES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.Transactions.TRANSACTIONS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.WalletBalances.WALLET_BALANCES;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
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

import com.uxplima.uxmessentials.economy.application.port.AccountSweep;
import com.uxplima.uxmessentials.economy.application.port.ItemDeliveryQueue;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SweepKind;
import com.uxplima.uxmessentials.economy.domain.SweepMode;
import com.uxplima.uxmessentials.economy.domain.SweepScope;
import com.uxplima.uxmessentials.economy.domain.SweepState;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.EconomySweepsRecord;
import com.uxplima.uxmessentials.persistence.runtime.IntegrityViolation;
import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import com.uxplima.uxmessentials.persistence.runtime.RowsRefused;
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
 * The {@link AccountSweep} journalled in {@code economy_sweeps}, for every {@link SweepMode}: a ledger sweep changes
 * the wallet rows, an item sweep queues rows in {@code economy_item_deliveries}, and a provider sweep hands groups of
 * players out to be paid through another plugin and settles what was done. Item and provider sweeps reach the players
 * of {@code player_names}, the list every server writes at each join.
 *
 * <p>Each ledger {@link #advance} is one short transaction over the next {@link #CHUNK} owners after the journal's cursor:
 * lock the journal row, list the owners, lock them (an owner a wallet purge removed in between is counted as gone),
 * give each owner without a balance a zero one (it reads exactly like no row), lock the chunk's balances, decide from
 * the locked figures, write them with one {@code UPDATE}, write their ledger rows with one {@code INSERT}, and move the
 * cursor. Locks are held for one chunk, milliseconds, so a payment or a Vault call on a server thread barely waits.
 * Because the cursor moves with the balances, a sweep that stops between chunks resumes exactly where it stopped,
 * and two runners of one sweep queue on the journal row rather than repeat a chunk.
 *
 * <p>The journal holds one unfinished sweep per currency (its unique {@code active_currency}), so a command given again
 * while a sweep is paused, or on another server, is refused instead of paying twice. A chunk the database refuses for
 * its rows (a figure a column cannot hold, a constraint) is tried once more past any ledger id an older server took,
 * then the sweep is marked failed; a sweep whose currency is no longer configured fails when it would resume. Both
 * free the currency, and so does an operator's cancel.
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

    /** Players per chunk handed to the other plugin: a few ticks of calls, and the most left uncertain by a stop. */
    static final int PROVIDER_CHUNK = 50;

    /** How long a chunk handed out to be paid counts as in its runner's hands before it is taken as abandoned. */
    static final Duration STALE_AFTER = Duration.ofMinutes(10);

    /** Runs of a chunk the database may roll back to break a lock cycle before the failure is passed on. */
    private static final int ATTEMPTS = 3;

    private static final String CREDIT = "CREDIT";
    private static final String DEBIT = "DEBIT";
    /** The states in which a sweep can still change accounts. */
    private static final List<String> UNFINISHED = List.of(SweepState.RUNNING.name(), SweepState.PAUSED.name());

    /** The width of the journal's {@code actor_name} column. */
    private static final int NAME_LENGTH = 64;

    /** The width of the journal's {@code last_error} column. */
    private static final int ERROR_LENGTH = 255;

    private final DSLContext dsl;
    private final IntToLongFunction ledgerIds;
    private final Runnable windLedgerIds;
    private final CurrencyRegistry currencies;
    private final Clock clock;
    private final Logger log;
    private final String runner;
    private final Duration staleAfter;

    /**
     * @param ledgerIds hands out the given number of consecutive ledger ids and returns the first, from the same
     *     sequence the buffered ledger writer draws from; it runs in a transaction of its own, so it is called before
     *     a chunk's transaction opens
     * @param windLedgerIds moves that sequence past every ledger id already written, for a chunk whose ledger rows met
     *     an id a server on an older release took; also a transaction of its own
     */
    public JooqAccountSweep(
            DSLContext dsl,
            IntToLongFunction ledgerIds,
            Runnable windLedgerIds,
            CurrencyRegistry currencies,
            Clock clock,
            Logger log) {
        this(
                dsl,
                ledgerIds,
                windLedgerIds,
                currencies,
                clock,
                log,
                UUID.randomUUID().toString(),
                STALE_AFTER);
    }

    /** Tests name the runner and shorten the window after which a handed-out chunk counts as abandoned. */
    JooqAccountSweep(
            DSLContext dsl,
            IntToLongFunction ledgerIds,
            Runnable windLedgerIds,
            CurrencyRegistry currencies,
            Clock clock,
            Logger log,
            String runner,
            Duration staleAfter) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.staleAfter = Objects.requireNonNull(staleAfter, "staleAfter");
        this.dsl = Objects.requireNonNull(dsl, "dsl");
        this.ledgerIds = Objects.requireNonNull(ledgerIds, "ledgerIds");
        this.windLedgerIds = Objects.requireNonNull(windLedgerIds, "windLedgerIds");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * The journal's unique {@code active_currency} decides: the insert of a second unfinished sweep of a currency is
     * refused by the database itself, so two servers opening at once get one sweep between them.
     */
    @Override
    public Opening open(
            SweepKind kind,
            SweepScope scope,
            SweepMode mode,
            Money amount,
            EconomyReason reason,
            PlayerRef actor,
            long at) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(actor, "actor");
        if (kind == SweepKind.CREDIT && amount.amount().signum() <= 0) {
            throw new IllegalArgumentException("a sweep credits a positive amount: " + amount.amount());
        }
        String currencyId = amount.currency().id().value();
        for (int attempt = 1; ; attempt++) {
            Sweep sweep = new Sweep(UUID.randomUUID().toString(), kind, scope, mode, amount, reason, actor, at);
            try {
                // Two servers inserting the same currency at once can deadlock on MySQL's unique-key locks; the
                // one rolled back tries again and then meets the other's row as the duplicate it is.
                Transactions.inTransactionRetrying(dsl, ATTEMPTS, tx -> insert(tx, sweep));
                return new Opened(sweep);
            } catch (RuntimeException refused) {
                if (!IntegrityViolation.causedBy(refused)) {
                    throw refused;
                }
                Optional<SweepStatus> existing = unfinished().stream()
                        .filter(status -> status.currency().equals(currencyId))
                        .findFirst();
                if (existing.isPresent()) {
                    return new Busy(existing.get());
                }
                // The sweep in the way ended between the refusal and the read; the currency is free again.
                if (attempt >= ATTEMPTS) {
                    throw refused;
                }
            }
        }
    }

    private static int insert(DSLContext tx, Sweep sweep) {
        Money amount = sweep.amount();
        return tx.insertInto(ECONOMY_SWEEPS)
                .set(ECONOMY_SWEEPS.ID, sweep.id())
                .set(ECONOMY_SWEEPS.KIND, sweep.kind().name())
                .set(ECONOMY_SWEEPS.SCOPE, sweep.scope().name())
                .set(ECONOMY_SWEEPS.MODE, sweep.mode().name())
                .set(ECONOMY_SWEEPS.CURRENCY, amount.currency().id().value())
                .set(ECONOMY_SWEEPS.AMOUNT, amount.amount())
                .set(ECONOMY_SWEEPS.REASON, sweep.reason().name())
                .set(ECONOMY_SWEEPS.ACTOR, sweep.actor().uuid().toString())
                .set(ECONOMY_SWEEPS.ACTOR_NAME, shortName(sweep.actor().name()))
                .set(ECONOMY_SWEEPS.STARTED_AT, sweep.startedAt())
                .set(ECONOMY_SWEEPS.AFTER_OWNER, "")
                .set(ECONOMY_SWEEPS.CHANGED, 0)
                .set(ECONOMY_SWEEPS.SKIPPED, 0)
                .set(ECONOMY_SWEEPS.GONE, 0)
                .set(ECONOMY_SWEEPS.UNCERTAIN, 0)
                .set(ECONOMY_SWEEPS.STATE, SweepState.RUNNING.name())
                .set(ECONOMY_SWEEPS.ACTIVE_CURRENCY, amount.currency().id().value())
                .execute();
    }

    /**
     * A chunk refused for its rows is tried once more after the ledger's ids are wound past every id written, since
     * a server on an older release may have taken the ones reserved. Refused again, nothing will change that: the
     * sweep is marked failed. Any other failure is thrown for the caller to try again later.
     */
    @Override
    public Step advance(Sweep sweep) {
        Objects.requireNonNull(sweep, "sweep");
        try {
            return runChunk(sweep, ledgerIds.applyAsLong(CHUNK));
        } catch (RuntimeException refused) {
            if (!RowsRefused.causedBy(refused)) {
                throw refused;
            }
        }
        windLedgerIds.run();
        try {
            return runChunk(sweep, ledgerIds.applyAsLong(CHUNK));
        } catch (RuntimeException refused) {
            if (!RowsRefused.causedBy(refused)) {
                throw refused;
            }
            return fail(sweep.id(), describe(refused));
        }
    }

    private Step runChunk(Sweep sweep, long firstId) {
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
    public void pause(Sweep sweep, String error) {
        Objects.requireNonNull(sweep, "sweep");
        Objects.requireNonNull(error, "error");
        Transactions.inTransaction(
                dsl,
                tx -> tx.update(ECONOMY_SWEEPS)
                        .set(ECONOMY_SWEEPS.STATE, SweepState.PAUSED.name())
                        .set(ECONOMY_SWEEPS.LAST_ERROR, clip(error))
                        .where(ECONOMY_SWEEPS.ID.eq(sweep.id()))
                        .and(ECONOMY_SWEEPS.STATE.in(UNFINISHED))
                        .execute());
    }

    @Override
    public List<Sweep> resumable() {
        List<Sweep> sweeps = new ArrayList<>();
        for (EconomySweepsRecord row : unfinishedRows()) {
            Optional<Currency> currency = currencies.find(CurrencyId.of(row.getCurrency()));
            if (currency.isEmpty()) {
                String error = "currency " + row.getCurrency() + " is no longer configured";
                fail(row.getId(), error);
                log.error(
                        "event=economy_sweep_failed id=" + row.getId() + " currency=" + row.getCurrency()
                                + " reason=currency_unknown changed=" + row.getChanged()
                                + " (configure the currency again and run the command anew to finish it)",
                        new IllegalStateException(error));
                continue;
            }
            sweeps.add(new Sweep(
                    row.getId(),
                    SweepKind.valueOf(row.getKind()),
                    SweepScope.valueOf(row.getScope()),
                    SweepMode.valueOf(row.getMode()),
                    Money.of(currency.get(), row.getAmount()),
                    EconomyReason.valueOf(row.getReason()),
                    actor(row),
                    row.getStartedAt()));
        }
        return sweeps;
    }

    @Override
    public List<SweepStatus> unfinished() {
        return unfinishedRows().stream().map(JooqAccountSweep::status).toList();
    }

    /**
     * Ends the sweep under the journal row's lock, so a chunk another server is running finishes first and the count
     * returned is every account the sweep changed.
     */
    @Override
    public Optional<SweepStatus> cancel(String id, long at) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            return Optional.empty();
        }
        List<String> matching = unfinishedRows().stream()
                .map(EconomySweepsRecord::getId)
                .filter(candidate -> candidate.startsWith(id))
                .toList();
        if (matching.size() != 1) {
            return Optional.empty();
        }
        String full = matching.getFirst();
        return Transactions.inTransaction(dsl, tx -> {
            takeWriteLock(tx);
            EconomySweepsRecord journal = lockJournal(tx, full);
            if (!SweepState.valueOf(journal.getState()).unfinished()) {
                return Optional.<SweepStatus>empty();
            }
            tx.update(ECONOMY_SWEEPS)
                    .set(ECONOMY_SWEEPS.STATE, SweepState.CANCELLED.name())
                    .setNull(ECONOMY_SWEEPS.ACTIVE_CURRENCY)
                    .set(ECONOMY_SWEEPS.FINISHED_AT, at)
                    .where(ECONOMY_SWEEPS.ID.eq(full))
                    .execute();
            journal.setState(SweepState.CANCELLED.name());
            return Optional.of(status(journal));
        });
    }

    /** Mark the sweep failed with {@code error}, unless it already ended, and say so in a step. */
    private Step fail(String id, String error) {
        return Transactions.inTransaction(dsl, tx -> {
            takeWriteLock(tx);
            EconomySweepsRecord journal = lockJournal(tx, id);
            SweepOutcome total = totals(journal);
            if (!SweepState.valueOf(journal.getState()).unfinished()) {
                return new Step(total, List.of(), Progress.ENDED);
            }
            tx.update(ECONOMY_SWEEPS)
                    .set(ECONOMY_SWEEPS.STATE, SweepState.FAILED.name())
                    .setNull(ECONOMY_SWEEPS.ACTIVE_CURRENCY)
                    .set(ECONOMY_SWEEPS.LAST_ERROR, clip(error))
                    .set(ECONOMY_SWEEPS.FINISHED_AT, clock.millis())
                    .where(ECONOMY_SWEEPS.ID.eq(id))
                    .execute();
            return new Step(total, List.of(), List.of(), Progress.FAILED, error);
        });
    }

    private List<EconomySweepsRecord> unfinishedRows() {
        return Transactions.inTransaction(
                dsl,
                tx -> tx.selectFrom(ECONOMY_SWEEPS)
                        .where(ECONOMY_SWEEPS.STATE.in(UNFINISHED))
                        .orderBy(ECONOMY_SWEEPS.STARTED_AT, ECONOMY_SWEEPS.ID)
                        .fetch());
    }

    private static SweepStatus status(EconomySweepsRecord row) {
        return new SweepStatus(
                row.getId(),
                SweepKind.valueOf(row.getKind()),
                SweepScope.valueOf(row.getScope()),
                SweepMode.valueOf(row.getMode()),
                row.getCurrency(),
                row.getAmount(),
                EconomyReason.valueOf(row.getReason()),
                actor(row),
                SweepState.valueOf(row.getState()),
                totals(row),
                row.getStartedAt(),
                row.getLastError());
    }

    private static PlayerRef actor(EconomySweepsRecord row) {
        return new PlayerRef(UUID.fromString(row.getActor()), row.getActorName());
    }

    /** One line saying what the database refused, short enough for the journal. */
    private static String describe(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private static String clip(String error) {
        return error.length() <= ERROR_LENGTH ? error : error.substring(0, ERROR_LENGTH);
    }

    /** On SQLite, make this transaction the file's one writer before it reads anything it will decide on. */
    private static void takeWriteLock(DSLContext tx) {
        if (sqlite(tx)) {
            // A write that matches no row still makes this transaction SQLite's one writer, waiting for the lock.
            tx.update(ECONOMY_SWEEPS)
                    .set(ECONOMY_SWEEPS.STATE, ECONOMY_SWEEPS.STATE)
                    .where(DSL.falseCondition())
                    .execute();
        }
    }

    /** One chunk of {@code sweep}, inside the transaction {@code tx}. */
    private Step chunk(DSLContext tx, Sweep sweep, long firstId) {
        takeWriteLock(tx);
        EconomySweepsRecord journal = lockJournal(tx, sweep.id());
        SweepOutcome total = totals(journal);
        if (!SweepState.valueOf(journal.getState()).unfinished()) {
            return new Step(total, List.of(), Progress.ENDED);
        }
        return switch (sweep.mode()) {
            case LEDGER -> ledgerChunk(tx, sweep, journal, firstId);
            case DEFERRED -> itemsChunk(tx, sweep, journal);
            case PROVIDER -> providerChunk(tx, sweep, journal);
        };
    }

    /** Mark the sweep done, freeing its currency. */
    private Step done(DSLContext tx, Sweep sweep, SweepOutcome total) {
        tx.update(ECONOMY_SWEEPS)
                .set(ECONOMY_SWEEPS.STATE, SweepState.DONE.name())
                .setNull(ECONOMY_SWEEPS.ACTIVE_CURRENCY)
                .setNull(ECONOMY_SWEEPS.LAST_ERROR)
                .set(ECONOMY_SWEEPS.FINISHED_AT, clock.millis())
                .where(ECONOMY_SWEEPS.ID.eq(sweep.id()))
                .execute();
        return new Step(total, List.of(), Progress.FINISHED);
    }

    /** Move the cursor past a chunk and add what it did, guarded on the cursor this chunk started from. */
    private static void moveCursor(
            DSLContext tx, Sweep sweep, EconomySweepsRecord journal, String last, SweepOutcome done) {
        int moved = tx.update(ECONOMY_SWEEPS)
                .set(ECONOMY_SWEEPS.AFTER_OWNER, last)
                .set(ECONOMY_SWEEPS.CHANGED, ECONOMY_SWEEPS.CHANGED.plus(done.changed()))
                .set(ECONOMY_SWEEPS.SKIPPED, ECONOMY_SWEEPS.SKIPPED.plus(done.skipped()))
                .set(ECONOMY_SWEEPS.GONE, ECONOMY_SWEEPS.GONE.plus(done.gone()))
                .set(ECONOMY_SWEEPS.UNCERTAIN, ECONOMY_SWEEPS.UNCERTAIN.plus(done.uncertain()))
                .set(ECONOMY_SWEEPS.STATE, SweepState.RUNNING.name())
                .setNull(ECONOMY_SWEEPS.LAST_ERROR)
                .where(ECONOMY_SWEEPS.ID.eq(sweep.id()))
                .and(ECONOMY_SWEEPS.AFTER_OWNER.eq(journal.getAfterOwner()))
                .execute();
        requireAll(moved, 1);
    }

    /**
     * Queue one delivery per player of the chunk, a give added to one already waiting. A reset first drops the
     * deliveries of the currency queued for those players and the credits queued for them while they were away, since
     * the reset decides what they hold; its own delivery then sets the balance when they are next online. A give being
     * handed over right now is dropped too: it came before the reset, and the reset is applied after it, since a
     * player's deliveries are handed over one at a time and the reset before any give that came after it.
     */
    private Step itemsChunk(DSLContext tx, Sweep sweep, EconomySweepsRecord journal) {
        SweepOutcome total = totals(journal);
        List<PlayerRef> players = nextPlayers(tx, journal.getAfterOwner(), CHUNK);
        if (players.isEmpty()) {
            return done(tx, sweep, total);
        }
        String currencyId = sweep.amount().currency().id().value();
        List<String> owners = new ArrayList<>(players.size());
        for (PlayerRef player : players) {
            owners.add(player.uuid().toString());
        }
        if (sweep.kind() == SweepKind.RESET) {
            tx.deleteFrom(ECONOMY_ITEM_DELIVERIES)
                    .where(ECONOMY_ITEM_DELIVERIES.CURRENCY.eq(currencyId))
                    .and(ECONOMY_ITEM_DELIVERIES.OWNER.in(owners))
                    .execute();
            tx.deleteFrom(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS)
                    .where(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.CURRENCY.eq(currencyId))
                    .and(ECONOMY_PENDING_PHYSICAL_TRANSACTIONS.PLAYER_UUID.in(owners))
                    .execute();
        }
        ItemDeliveryQueue.Kind kind =
                sweep.kind() == SweepKind.CREDIT ? ItemDeliveryQueue.Kind.GIVE : ItemDeliveryQueue.Kind.RESET;
        JooqItemDeliveries.queue(
                tx,
                clock.millis(),
                sweep.id(),
                owners,
                currencyId,
                kind,
                sweep.amount().amount(),
                sweep.reason());
        SweepOutcome done = new SweepOutcome(owners.size(), 0, 0);
        moveCursor(tx, sweep, journal, owners.getLast(), done);
        List<UUID> queued = new ArrayList<>(players.size());
        for (PlayerRef player : players) {
            queued.add(player.uuid());
        }
        return new Step(total.plus(done), queued, Progress.MORE);
    }

    /**
     * Hand the next players out to be paid through the plugin that keeps the balances. A chunk another runner still
     * has out, and has had out for less than the stale window, makes this step wait; one held longer, or held by this
     * runner's earlier life, is counted as uncertain and logged, and the sweep goes on past it.
     */
    private Step providerChunk(DSLContext tx, Sweep sweep, EconomySweepsRecord journal) {
        SweepOutcome total = totals(journal);
        String pending = journal.getPendingOwners();
        if (pending != null) {
            Long pendingAt = journal.getPendingAt();
            boolean fresh = pendingAt != null && clock.millis() - pendingAt < staleAfter.toMillis();
            if (fresh && !runner.equals(journal.getPendingBy())) {
                return new Step(total, List.of(), Progress.BUSY);
            }
            List<String> lost = List.of(pending.split(","));
            log.error(
                    "event=economy_sweep_uncertain id=" + sweep.id() + " players=" + lost.size() + " owners=" + pending
                            + " (handed out to be paid and never settled; check these players' balances)",
                    new IllegalStateException("sweep " + sweep.id() + " chunk abandoned"));
            tx.update(ECONOMY_SWEEPS)
                    .set(ECONOMY_SWEEPS.UNCERTAIN, ECONOMY_SWEEPS.UNCERTAIN.plus(lost.size()))
                    .setNull(ECONOMY_SWEEPS.PENDING_OWNERS)
                    .setNull(ECONOMY_SWEEPS.PENDING_BY)
                    .setNull(ECONOMY_SWEEPS.PENDING_AT)
                    .where(ECONOMY_SWEEPS.ID.eq(sweep.id()))
                    .execute();
            total = total.plus(new SweepOutcome(0, 0, 0, lost.size()));
        }
        List<PlayerRef> players = nextPlayers(tx, journal.getAfterOwner(), PROVIDER_CHUNK);
        if (players.isEmpty()) {
            return done(tx, sweep, total);
        }
        List<String> owners = new ArrayList<>(players.size());
        for (PlayerRef player : players) {
            owners.add(player.uuid().toString());
        }
        int moved = tx.update(ECONOMY_SWEEPS)
                .set(ECONOMY_SWEEPS.AFTER_OWNER, owners.getLast())
                .set(ECONOMY_SWEEPS.PENDING_OWNERS, String.join(",", owners))
                .set(ECONOMY_SWEEPS.PENDING_BY, runner)
                .set(ECONOMY_SWEEPS.PENDING_AT, clock.millis())
                .set(ECONOMY_SWEEPS.STATE, SweepState.RUNNING.name())
                .setNull(ECONOMY_SWEEPS.LAST_ERROR)
                .where(ECONOMY_SWEEPS.ID.eq(sweep.id()))
                .and(ECONOMY_SWEEPS.AFTER_OWNER.eq(journal.getAfterOwner()))
                .execute();
        requireAll(moved, 1);
        return new Step(total, List.of(), players, Progress.MORE, null);
    }

    @Override
    public void settle(Sweep sweep, SweepOutcome done) {
        Objects.requireNonNull(sweep, "sweep");
        Objects.requireNonNull(done, "done");
        Transactions.inTransaction(dsl, tx -> {
            takeWriteLock(tx);
            lockJournal(tx, sweep.id());
            // Counted whatever the sweep's state: the calls were made, even if it was cancelled meanwhile.
            return tx.update(ECONOMY_SWEEPS)
                    .set(ECONOMY_SWEEPS.CHANGED, ECONOMY_SWEEPS.CHANGED.plus(done.changed()))
                    .set(ECONOMY_SWEEPS.SKIPPED, ECONOMY_SWEEPS.SKIPPED.plus(done.skipped()))
                    .set(ECONOMY_SWEEPS.GONE, ECONOMY_SWEEPS.GONE.plus(done.gone()))
                    .set(ECONOMY_SWEEPS.UNCERTAIN, ECONOMY_SWEEPS.UNCERTAIN.plus(done.uncertain()))
                    .setNull(ECONOMY_SWEEPS.PENDING_OWNERS)
                    .setNull(ECONOMY_SWEEPS.PENDING_BY)
                    .setNull(ECONOMY_SWEEPS.PENDING_AT)
                    .where(ECONOMY_SWEEPS.ID.eq(sweep.id()))
                    .and(ECONOMY_SWEEPS.PENDING_BY.eq(runner))
                    .execute();
        });
    }

    /** The next players after {@code after} in id order, from the list every server writes at each join. */
    private static List<PlayerRef> nextPlayers(DSLContext tx, String after, int limit) {
        return tx.select(PLAYER_NAMES.UUID, PLAYER_NAMES.NAME)
                .from(PLAYER_NAMES)
                .where(PLAYER_NAMES.UUID.gt(after))
                .orderBy(PLAYER_NAMES.UUID)
                .limit(limit)
                .fetch(row -> new PlayerRef(UUID.fromString(row.value1()), row.value2()));
    }

    private static SweepOutcome totals(EconomySweepsRecord row) {
        return new SweepOutcome(row.getChanged(), row.getSkipped(), row.getGone(), row.getUncertain());
    }

    /** One chunk of a ledger sweep: the balances and their ledger rows change in this transaction. */
    private Step ledgerChunk(DSLContext tx, Sweep sweep, EconomySweepsRecord journal, long firstId) {
        SweepOutcome total = totals(journal);
        List<String> listed = nextOwners(tx, sweep.scope(), journal.getAfterOwner());
        if (listed.isEmpty()) {
            return done(tx, sweep, total);
        }
        List<String> owners = lockOwners(tx, listed);
        ChunkResult result = sweep.kind() == SweepKind.CREDIT
                ? credit(tx, owners, sweep.amount())
                : reset(tx, owners, sweep.amount());
        writeLedger(tx, result.rows(), sweep, firstId);
        SweepOutcome done = new SweepOutcome(result.rows().size(), result.skipped(), listed.size() - owners.size());
        moveCursor(tx, sweep, journal, listed.getLast(), done);
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
        return name.length() <= NAME_LENGTH ? name : name.substring(0, NAME_LENGTH);
    }

    /** What one chunk decided: a ledger row per changed account, and how many it left out for the currency's limit. */
    private record ChunkResult(List<LedgerRow> rows, int skipped) {}

    /** One account's ledger row: a single-sided credit or debit, shaped like the buffered writer's. */
    private record LedgerRow(String owner, BigDecimal amount, String type) {}
}
