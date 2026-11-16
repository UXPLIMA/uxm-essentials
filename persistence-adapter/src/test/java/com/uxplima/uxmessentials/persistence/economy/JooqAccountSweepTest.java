package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyOwners.ECONOMY_OWNERS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerNames.PLAYER_NAMES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.Transactions.TRANSACTIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntToLongFunction;

import com.uxplima.uxmessentials.economy.application.port.AccountSweep.Busy;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.Opened;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.Opening;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.Progress;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.Step;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.Sweep;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.SweepOutcome;
import com.uxplima.uxmessentials.economy.application.port.AccountSweep.SweepStatus;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SweepKind;
import com.uxplima.uxmessentials.economy.domain.SweepScope;
import com.uxplima.uxmessentials.economy.domain.SweepState;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A sweep reaches every account it should, writes one ledger row per changed account with its balance, holds its locks
 * one chunk at a time, and never changes an account twice: not when it stops part way and carries on, not when two
 * runners advance it at once, and not when the command is given again while it is unfinished. One the database
 * refuses for good, or whose currency is gone, ends as failed; an operator can cancel one.
 */
@EveryBackend
class JooqAccountSweepTest {

    private static final long AT = Instant.parse("2030-01-01T00:00:00Z").toEpochMilli();
    private static final PlayerRef ADMIN = owner(9_999, "Admin");
    private static final PlayerRef ALEX = owner(1, "Alex");
    private static final PlayerRef SAM = owner(2, "Sam");
    private static final PlayerRef TOWN = owner(3, "town-Oakvale");
    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .max(new BigDecimal("1000"))
            .starting(new BigDecimal("50"))
            .build();
    private static final Currency GEMS = Currency.builder(CurrencyId.of("gems")).build();
    private static final CurrencyRegistry CURRENCIES = CurrencyRegistry.of(List.of(COINS, GEMS), COINS.id());
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(AT), ZoneOffset.UTC);

    private DSLContext dsl;
    private JooqWalletRepository wallets;
    private TransactionTelemetry telemetry;
    private JooqAccountSweep sweeps;
    private Executor servers = ForkJoinPool.commonPool();
    private TestDatabase backend;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
        if (database.backend().isFile()) {
            // The plugin holds one connection to SQLite, so its writers never overlap; the test's plain data source
            // would open a connection per thread and contend for the file instead.
            servers = Executors.newSingleThreadExecutor();
        }
        wallets = new JooqWalletRepository(dsl, CURRENCIES, CLOCK);
        telemetry = new TransactionTelemetry(dsl, new IdleScheduler(), new SilentLogger(), Duration.ofHours(1));
        telemetry.start();
        sweeps = sweeps(telemetry::reserveIds, telemetry::windIdsForward);
        backend = database;
    }

    private JooqAccountSweep sweeps(IntToLongFunction ids, Runnable windForward) {
        return new JooqAccountSweep(dsl, ids, windForward, CURRENCIES, CLOCK, new RecordingLogger());
    }

    @Test
    void aGiveAllOverEveryAccountCreditsEachOnceAndWritesItsLedgerRow() {
        wallets.credit(ALEX, coins("10"));
        wallets.credit(SAM, gems("3"));
        wallets.ensureOwner(TOWN);

        SweepOutcome outcome = runToEnd(open(SweepKind.CREDIT, SweepScope.ALL, coins("25")));

        assertThat(outcome).isEqualTo(new SweepOutcome(3, 0, 0));
        assertThat(balance(ALEX, COINS)).isEqualByComparingTo("35");
        assertThat(balance(SAM, COINS)).isEqualByComparingTo("25");
        assertThat(balance(TOWN, COINS)).isEqualByComparingTo("25");
        assertThat(balance(SAM, GEMS)).isEqualByComparingTo("3");
        assertThat(ledger())
                .containsExactlyInAnyOrder(
                        "CREDIT:" + ALEX.uuid() + ":25:ADMIN_GIVEALL",
                        "CREDIT:" + SAM.uuid() + ":25:ADMIN_GIVEALL",
                        "CREDIT:" + TOWN.uuid() + ":25:ADMIN_GIVEALL");
    }

    @Test
    void aSweepOfPlayersLeavesAccountsOtherPluginsOpenedAlone() {
        joined(ALEX);
        joined(SAM);
        wallets.credit(ALEX, coins("80"));
        wallets.credit(SAM, coins("10"));
        wallets.credit(TOWN, coins("900"));

        SweepOutcome outcome = runToEnd(open(SweepKind.RESET, SweepScope.PLAYERS, starting(COINS)));

        assertThat(outcome).isEqualTo(new SweepOutcome(2, 0, 0));
        assertThat(balance(ALEX, COINS)).isEqualByComparingTo("50");
        assertThat(balance(SAM, COINS)).isEqualByComparingTo("50");
        assertThat(balance(TOWN, COINS)).isEqualByComparingTo("900");
        assertThat(ledger())
                .containsExactlyInAnyOrder(
                        "DEBIT:" + ALEX.uuid() + ":30:ADMIN_RESETALL", "CREDIT:" + SAM.uuid() + ":40:ADMIN_RESETALL");
    }

    @Test
    void aGiveAllLeavesOutTheAccountsItWouldPushPastTheLimit() {
        wallets.credit(ALEX, coins("990"));
        wallets.credit(SAM, coins("975"));

        SweepOutcome outcome = runToEnd(open(SweepKind.CREDIT, SweepScope.ALL, coins("25")));

        assertThat(outcome).isEqualTo(new SweepOutcome(1, 1, 0));
        assertThat(balance(ALEX, COINS)).isEqualByComparingTo("990");
        assertThat(balance(SAM, COINS)).isEqualByComparingTo("1000");
        assertThat(ledger()).containsExactly("CREDIT:" + SAM.uuid() + ":25:ADMIN_GIVEALL");
    }

    @Test
    void aResetToZeroLeavesAccountsThatNeverHeldTheCurrencyAlone() {
        wallets.credit(ALEX, gems("4"));
        wallets.credit(SAM, gems("9"));

        SweepOutcome outcome = runToEnd(open(SweepKind.RESET, SweepScope.ALL, Money.zero(GEMS)));

        assertThat(outcome).isEqualTo(new SweepOutcome(2, 0, 0));
        assertThat(balance(ALEX, GEMS)).isEqualByComparingTo("0");
        assertThat(wallets.findByOwner(ALEX).orElseThrow().balances()).doesNotContainKey(COINS);
        assertThat(ledger()).hasSize(2);
    }

    @Test
    void aSweepStoppedPartWayCarriesOnWhereItStoppedAndPaysNobodyTwice() {
        int accounts = JooqAccountSweep.CHUNK * 2 + 7;
        insertOwners(accounts);
        Sweep sweep = open(SweepKind.CREDIT, SweepScope.ALL, coins("1"));
        assertThat(sweeps.advance(sweep).progress()).isEqualTo(Progress.MORE);
        // The second chunk's ledger insert meets an id another writer took, after its balances were written, and the
        // connection is lost before it can move past that id.
        long taken = telemetry.reserveIds(1);
        writeLedgerRow(taken);
        JooqAccountSweep stopping = sweeps(count -> taken, () -> {
            throw new IllegalStateException("connection lost");
        });

        assertThatThrownBy(() -> stopping.advance(sweep)).isInstanceOf(RuntimeException.class);

        assertThat(sweeps.resumable()).extracting(Sweep::id).containsExactly(sweep.id());
        assertThat(ledger()).hasSize(JooqAccountSweep.CHUNK + 1);
        SweepOutcome outcome = runToEnd(sweep);
        assertThat(outcome).isEqualTo(new SweepOutcome(accounts, 0, 0));
        assertThat(wallets.top(COINS, accounts + 1))
                .hasSize(accounts)
                .allSatisfy(row -> assertThat(row.balance().amount()).isEqualByComparingTo("1"));
        assertThat(ledger()).hasSize(accounts + 1);
        assertThat(sweeps.unfinished()).isEmpty();
    }

    @Test
    void ledgerIdsAServerOnAnOlderReleaseTookAreSteppedOverAndTheChunkLands() {
        insertOwners(5);
        long next = telemetry.reserveIds(1) + 1;
        for (long id = next; id < next + JooqAccountSweep.CHUNK * 2; id++) {
            writeLedgerRow(id);
        }

        SweepOutcome outcome = runToEnd(open(SweepKind.CREDIT, SweepScope.ALL, coins("1")));

        assertThat(outcome).isEqualTo(new SweepOutcome(5, 0, 0));
        assertThat(ledger()).hasSize(JooqAccountSweep.CHUNK * 2 + 5);
    }

    @Test
    void aChunkTheDatabaseRefusesForGoodFailsTheSweepAndChangesNothingOfIt() {
        if (backend.backend().isFile()) {
            // SQLite keeps any figure in a DECIMAL column; this refuses the figures the server databases cannot hold.
            dsl.execute("CREATE TRIGGER refuse_huge_balance BEFORE UPDATE ON wallet_balances"
                    + " WHEN NEW.amount >= 1e16 BEGIN SELECT RAISE(ABORT, 'out of range'); END");
        }
        Currency vast = Currency.builder(CurrencyId.of("coins"))
                .max(new BigDecimal("1000000000000000000"))
                .build();
        wallets.credit(ALEX, coins("10"));
        wallets.credit(SAM, coins("20"));
        Money huge = Money.of(vast, new BigDecimal("9999999999999999"));

        Sweep sweep = open(SweepKind.CREDIT, SweepScope.ALL, huge);
        Step step = sweeps.advance(sweep);

        assertThat(step.progress()).isEqualTo(Progress.FAILED);
        assertThat(step.error()).isNotBlank();
        assertThat(balance(ALEX, COINS)).isEqualByComparingTo("10");
        assertThat(balance(SAM, COINS)).isEqualByComparingTo("20");
        assertThat(ledger()).isEmpty();
        assertThat(sweeps.unfinished()).isEmpty();
        assertThat(sweeps.advance(sweep).progress()).isEqualTo(Progress.ENDED);
        // A failed sweep no longer holds its currency.
        assertThat(sweeps.open(SweepKind.CREDIT, SweepScope.ALL, coins("1"), EconomyReason.ADMIN_GIVEALL, ADMIN, AT))
                .isInstanceOf(Opened.class);
    }

    @Test
    void aCurrencyWithAnUnfinishedSweepRefusesASecondOnAnyServer() {
        wallets.ensureOwner(ALEX);
        Sweep first = open(SweepKind.CREDIT, SweepScope.ALL, coins("5"));
        JooqAccountSweep elsewhere = sweeps(telemetry::reserveIds, telemetry::windIdsForward);

        Opening second = elsewhere.open(
                SweepKind.RESET, SweepScope.PLAYERS, starting(COINS), EconomyReason.ADMIN_RESETALL, ADMIN, AT);

        assertThat(second).isInstanceOfSatisfying(Busy.class, busy -> {
            assertThat(busy.existing().id()).isEqualTo(first.id());
            assertThat(busy.existing().state()).isEqualTo(SweepState.RUNNING);
        });
        assertThat(elsewhere.open(SweepKind.CREDIT, SweepScope.ALL, gems("1"), EconomyReason.ADMIN_GIVEALL, ADMIN, AT))
                .isInstanceOf(Opened.class);
        runToEnd(first);
        assertThat(elsewhere.open(
                        SweepKind.RESET, SweepScope.PLAYERS, starting(COINS), EconomyReason.ADMIN_RESETALL, ADMIN, AT))
                .isInstanceOf(Opened.class);
    }

    @Test
    void twoServersOpeningASweepOfOneCurrencyAtOnceGetOneBetweenThem() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Opening>> openings = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            JooqAccountSweep server = sweeps(telemetry::reserveIds, telemetry::windIdsForward);
            openings.add(CompletableFuture.supplyAsync(
                    () -> {
                        await(start);
                        return server.open(
                                SweepKind.CREDIT, SweepScope.ALL, coins("5"), EconomyReason.ADMIN_GIVEALL, ADMIN, AT);
                    },
                    servers));
        }
        start.countDown();

        int opened = 0;
        for (CompletableFuture<Opening> opening : openings) {
            if (opening.get(60, TimeUnit.SECONDS) instanceof Opened) {
                opened++;
            }
        }
        assertThat(opened).isEqualTo(1);
        assertThat(sweeps.unfinished()).hasSize(1);
    }

    @Test
    void aCancelledSweepKeepsWhatItChangedAndChangesNothingMore() {
        int accounts = JooqAccountSweep.CHUNK + 9;
        insertOwners(accounts);
        Sweep sweep = open(SweepKind.CREDIT, SweepScope.ALL, coins("1"));
        sweeps.advance(sweep);

        Optional<SweepStatus> cancelled = sweeps(telemetry::reserveIds, telemetry::windIdsForward)
                .cancel(sweep.id().substring(0, 8), AT);

        assertThat(cancelled).hasValueSatisfying(status -> {
            assertThat(status.id()).isEqualTo(sweep.id());
            assertThat(status.state()).isEqualTo(SweepState.CANCELLED);
            assertThat(status.total().changed()).isEqualTo(JooqAccountSweep.CHUNK);
        });
        assertThat(sweeps.advance(sweep).progress()).isEqualTo(Progress.ENDED);
        assertThat(ledger()).hasSize(JooqAccountSweep.CHUNK);
        assertThat(sweeps.unfinished()).isEmpty();
        assertThat(sweeps.cancel(sweep.id(), AT)).isEmpty();
        assertThat(sweeps.open(SweepKind.CREDIT, SweepScope.ALL, coins("1"), EconomyReason.ADMIN_GIVEALL, ADMIN, AT))
                .isInstanceOf(Opened.class);
    }

    @Test
    void aPausedSweepIsListedWithItsErrorAndRunsOnWhenTriedAgain() {
        wallets.ensureOwner(ALEX);
        Sweep sweep = open(SweepKind.CREDIT, SweepScope.ALL, coins("5"));

        sweeps.pause(sweep, "SQLTransientConnectionException: connection lost");

        assertThat(sweeps.unfinished()).singleElement().satisfies(status -> {
            assertThat(status.state()).isEqualTo(SweepState.PAUSED);
            assertThat(status.lastError()).contains("connection lost");
        });
        assertThat(sweeps.resumable()).extracting(Sweep::id).containsExactly(sweep.id());
        assertThat(runToEnd(sweep)).isEqualTo(new SweepOutcome(1, 0, 0));
    }

    @Test
    void aSweepWhoseCurrencyIsNoLongerConfiguredFailsInsteadOfWaitingForEver() {
        wallets.ensureOwner(ALEX);
        Sweep sweep = open(SweepKind.CREDIT, SweepScope.ALL, gems("5"));
        RecordingLogger log = new RecordingLogger();
        JooqAccountSweep withoutGems = new JooqAccountSweep(
                dsl,
                telemetry::reserveIds,
                telemetry::windIdsForward,
                CurrencyRegistry.of(List.of(COINS), COINS.id()),
                CLOCK,
                log);

        assertThat(withoutGems.resumable()).isEmpty();

        assertThat(log.errors).singleElement().asString().contains("event=economy_sweep_failed", sweep.id());
        assertThat(sweeps.unfinished()).isEmpty();
        assertThat(sweeps.advance(sweep).progress()).isEqualTo(Progress.ENDED);
    }

    @Test
    void twoRunnersOfOneSweepTakeTurnsAndOnlyOneFinishesIt() throws Exception {
        int accounts = JooqAccountSweep.CHUNK * 3 + 11;
        insertOwners(accounts);
        Sweep sweep = open(SweepKind.CREDIT, SweepScope.ALL, coins("1"));
        JooqAccountSweep elsewhere = sweeps(telemetry::reserveIds, telemetry::windIdsForward);
        AtomicInteger finishes = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Void>> runners = new ArrayList<>();
        for (JooqAccountSweep runner : List.of(sweeps, elsewhere)) {
            runners.add(CompletableFuture.runAsync(
                    () -> {
                        await(start);
                        while (true) {
                            Step step = runner.advance(sweep);
                            if (step.progress() == Progress.FINISHED) {
                                finishes.incrementAndGet();
                            }
                            if (step.progress() != Progress.MORE) {
                                return;
                            }
                        }
                    },
                    servers));
        }
        start.countDown();
        for (CompletableFuture<Void> runner : runners) {
            runner.get(120, TimeUnit.SECONDS);
        }

        assertThat(finishes).hasValue(1);
        assertThat(wallets.top(COINS, accounts + 1))
                .hasSize(accounts)
                .allSatisfy(row -> assertThat(row.balance().amount()).isEqualByComparingTo("1"));
        assertThat(ledger()).hasSize(accounts);
    }

    @Test
    void paymentsRacingAGiveAllAreNeitherLostNorCountedTwice() throws Exception {
        int accounts = 40;
        List<PlayerRef> owners = new ArrayList<>();
        for (int i = 0; i < accounts; i++) {
            PlayerRef owner = owner(100 + i, "P" + i);
            owners.add(owner);
            wallets.credit(owner, coins("100"));
        }
        int[] net = new int[accounts];
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<int[]>> payments = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            int from = i % accounts;
            int to = (i + 1) % accounts;
            payments.add(CompletableFuture.supplyAsync(
                    () -> {
                        await(start);
                        try {
                            return wallets.transfer(owners.get(from), owners.get(to), coins("1"))
                                            .isOk()
                                    ? new int[] {from, to}
                                    : new int[0];
                        } catch (RuntimeException failed) {
                            // A payment the database could not complete moved nothing.
                            return new int[0];
                        }
                    },
                    servers));
        }
        CompletableFuture<SweepOutcome> giveAll = CompletableFuture.supplyAsync(
                () -> {
                    await(start);
                    return runToEnd(open(SweepKind.CREDIT, SweepScope.ALL, coins("10")));
                },
                servers);
        start.countDown();

        assertThat(giveAll.get(60, TimeUnit.SECONDS)).isEqualTo(new SweepOutcome(accounts, 0, 0));
        for (CompletableFuture<int[]> payment : payments) {
            int[] moved = payment.get(60, TimeUnit.SECONDS);
            if (moved.length == 2) {
                net[moved[0]]--;
                net[moved[1]]++;
            }
        }
        for (int i = 0; i < accounts; i++) {
            assertThat(balance(owners.get(i), COINS)).isEqualByComparingTo(BigDecimal.valueOf(110L + net[i]));
        }
        assertThat(ledger()).hasSize(accounts);
    }

    @Test
    void theJournalListsTheSweepsStillRunningForAnyServerToCarryOn() {
        wallets.ensureOwner(ALEX);
        Sweep running = open(SweepKind.CREDIT, SweepScope.ALL, coins("5"));
        Sweep done = open(SweepKind.RESET, SweepScope.PLAYERS, Money.zero(GEMS));
        runToEnd(done);

        List<Sweep> unfinished =
                sweeps(telemetry::reserveIds, telemetry::windIdsForward).resumable();

        assertThat(unfinished).singleElement().satisfies(sweep -> {
            assertThat(sweep.id()).isEqualTo(running.id());
            assertThat(sweep.kind()).isEqualTo(SweepKind.CREDIT);
            assertThat(sweep.scope()).isEqualTo(SweepScope.ALL);
            assertThat(sweep.amount()).isEqualTo(coins("5"));
            assertThat(sweep.actor()).isEqualTo(ADMIN);
            assertThat(sweep.startedAt()).isEqualTo(AT);
        });
        assertThat(sweeps.advance(done).progress()).isEqualTo(Progress.ENDED);
    }

    private Sweep open(SweepKind kind, SweepScope scope, Money amount) {
        EconomyReason reason = kind == SweepKind.CREDIT ? EconomyReason.ADMIN_GIVEALL : EconomyReason.ADMIN_RESETALL;
        return ((Opened) sweeps.open(kind, scope, amount, reason, ADMIN, AT)).sweep();
    }

    private SweepOutcome runToEnd(Sweep sweep) {
        while (true) {
            Step step = sweeps.advance(sweep);
            if (step.progress() != Progress.MORE) {
                return step.total();
            }
        }
    }

    private void joined(PlayerRef player) {
        dsl.transaction(configuration -> configuration
                .dsl()
                .insertInto(PLAYER_NAMES)
                .set(PLAYER_NAMES.UUID, player.uuid().toString())
                .set(PLAYER_NAMES.NAME, player.name())
                .set(PLAYER_NAMES.LOWER_NAME, player.name().toLowerCase(java.util.Locale.ROOT))
                .set(PLAYER_NAMES.LAST_SEEN, AT)
                .execute());
    }

    /** Owners with no balance at all, committed in one transaction: a server pool does not autocommit. */
    private void insertOwners(int count) {
        dsl.transaction(configuration -> {
            var insert = configuration
                    .dsl()
                    .insertInto(ECONOMY_OWNERS, ECONOMY_OWNERS.OWNER, ECONOMY_OWNERS.NAME, ECONOMY_OWNERS.CREATED_AT);
            for (int i = 0; i < count; i++) {
                insert = insert.values(owner(1000 + i, "N" + i).uuid().toString(), "N" + i, AT);
            }
            insert.execute();
        });
    }

    private void writeLedgerRow(long id) {
        dsl.transaction(configuration -> configuration
                .dsl()
                .insertInto(TRANSACTIONS)
                .set(TRANSACTIONS.ID, id)
                .set(TRANSACTIONS.TS, AT)
                .set(TRANSACTIONS.TO_OWNER, ALEX.uuid().toString())
                .set(TRANSACTIONS.CURRENCY, "coins")
                .set(TRANSACTIONS.AMOUNT, BigDecimal.ONE)
                .set(TRANSACTIONS.TYPE, "CREDIT")
                .set(TRANSACTIONS.REASON, "ADMIN_GIVE")
                .execute());
    }

    /** Each ledger row as {@code TYPE:owner:amount:REASON}, the owner being whichever side the row names. */
    private List<String> ledger() {
        return dsl.selectFrom(TRANSACTIONS).fetch().map(row -> {
            String owner = row.getToOwner() != null ? row.getToOwner() : row.getFromOwner();
            return row.getType() + ":" + owner + ":"
                    + row.getAmount().stripTrailingZeros().toPlainString() + ":" + row.getReason();
        });
    }

    private BigDecimal balance(PlayerRef owner, Currency currency) {
        return wallets.findByOwner(owner).orElseThrow().balanceOf(currency).amount();
    }

    private static PlayerRef owner(int n, String name) {
        return new PlayerRef(new UUID(0L, n), name);
    }

    private static Money coins(String amount) {
        return Money.of(COINS, new BigDecimal(amount));
    }

    private static Money gems(String amount) {
        return Money.of(GEMS, new BigDecimal(amount));
    }

    private static Money starting(Currency currency) {
        return Money.of(currency, currency.starting());
    }

    private static void await(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Never runs the telemetry's flush loop: a test flushes by hand. */
    private static final class IdleScheduler implements Scheduler {

        @Override
        public void onGlobal(Runnable task) {}

        @Override
        public void onRegion(Position position, Runnable task) {}

        @Override
        public void onEntity(PlayerRef player, Runnable task) {}

        @Override
        public void async(Runnable task) {}

        @Override
        public void asyncAfter(Duration delay, Runnable task) {}
    }

    private static final class SilentLogger implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** Keeps the error lines, which are what an operator would be told. */
    private static final class RecordingLogger implements Logger {

        private final List<String> errors = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {
            errors.add(message);
        }

        @Override
        public void debug(String message, Object... args) {}
    }
}
