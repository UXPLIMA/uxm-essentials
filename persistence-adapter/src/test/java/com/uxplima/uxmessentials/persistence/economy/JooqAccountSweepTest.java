package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyOwners.ECONOMY_OWNERS;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.economy.application.port.AccountSweep.SweepOutcome;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
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
 * A sweep reaches every account the ledger knows, writes one ledger row per changed account with its balance, and
 * is all or nothing: a sweep that fails part way leaves every balance and the ledger as they were.
 */
@EveryBackend
class JooqAccountSweepTest {

    private static final long AT = Instant.parse("2030-01-01T00:00:00Z").toEpochMilli();
    private static final PlayerRef ALEX = owner(1, "Alex");
    private static final PlayerRef SAM = owner(2, "Sam");
    private static final PlayerRef ROBIN = owner(3, "Robin");
    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .max(new BigDecimal("1000"))
            .starting(new BigDecimal("50"))
            .build();
    private static final Currency GEMS = Currency.builder(CurrencyId.of("gems")).build();

    private DSLContext dsl;
    private JooqWalletRepository wallets;
    private TransactionTelemetry telemetry;
    private JooqAccountSweep sweep;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
        wallets = new JooqWalletRepository(
                dsl,
                CurrencyRegistry.of(List.of(COINS, GEMS), COINS.id()),
                Clock.fixed(Instant.ofEpochMilli(AT), ZoneOffset.UTC));
        telemetry = new TransactionTelemetry(dsl, new IdleScheduler(), new SilentLogger(), Duration.ofHours(1));
        telemetry.start();
        sweep = new JooqAccountSweep(dsl, telemetry::reserveIds);
    }

    @Test
    void aGiveAllCreditsEveryKnownAccountAndWritesOneLedgerRowForEach() {
        wallets.credit(ALEX, coins("10"));
        wallets.credit(SAM, gems("3"));
        wallets.ensureOwner(ROBIN);

        SweepOutcome outcome = sweep.creditEvery(coins("25"), EconomyReason.ADMIN_GIVEALL, AT);

        assertThat(outcome).isEqualTo(new SweepOutcome(3, 0));
        assertThat(balance(ALEX, COINS)).isEqualByComparingTo("35");
        assertThat(balance(SAM, COINS)).isEqualByComparingTo("25");
        assertThat(balance(ROBIN, COINS)).isEqualByComparingTo("25");
        assertThat(balance(SAM, GEMS)).isEqualByComparingTo("3");
        assertThat(ledger())
                .containsExactlyInAnyOrder(
                        "CREDIT:" + ALEX.uuid() + ":25:ADMIN_GIVEALL",
                        "CREDIT:" + SAM.uuid() + ":25:ADMIN_GIVEALL",
                        "CREDIT:" + ROBIN.uuid() + ":25:ADMIN_GIVEALL");
    }

    @Test
    void aGiveAllLeavesOutTheAccountsItWouldPushPastTheLimit() {
        wallets.credit(ALEX, coins("990"));
        wallets.credit(SAM, coins("975"));

        SweepOutcome outcome = sweep.creditEvery(coins("25"), EconomyReason.ADMIN_GIVEALL, AT);

        assertThat(outcome).isEqualTo(new SweepOutcome(1, 1));
        assertThat(balance(ALEX, COINS)).isEqualByComparingTo("990");
        assertThat(balance(SAM, COINS)).isEqualByComparingTo("1000");
        assertThat(ledger()).containsExactly("CREDIT:" + SAM.uuid() + ":25:ADMIN_GIVEALL");
    }

    @Test
    void aResetAllPutsEveryAccountAtTheStartingBalanceAndRecordsEachChange() {
        wallets.credit(ALEX, coins("80"));
        wallets.credit(SAM, coins("50"));
        wallets.credit(ROBIN, gems("1"));

        SweepOutcome outcome = sweep.resetEvery(starting(COINS), EconomyReason.ADMIN_RESETALL, AT);

        assertThat(outcome).isEqualTo(new SweepOutcome(2, 0));
        assertThat(balance(ALEX, COINS)).isEqualByComparingTo("50");
        assertThat(balance(SAM, COINS)).isEqualByComparingTo("50");
        assertThat(balance(ROBIN, COINS)).isEqualByComparingTo("50");
        assertThat(balance(ROBIN, GEMS)).isEqualByComparingTo("1");
        assertThat(ledger())
                .containsExactlyInAnyOrder(
                        "DEBIT:" + ALEX.uuid() + ":30:ADMIN_RESETALL", "CREDIT:" + ROBIN.uuid() + ":50:ADMIN_RESETALL");
    }

    @Test
    void aResetAllToZeroLeavesAccountsThatNeverHeldTheCurrencyAlone() {
        wallets.credit(ALEX, gems("4"));
        wallets.credit(SAM, gems("9"));

        SweepOutcome outcome = sweep.resetEvery(Money.zero(GEMS), EconomyReason.ADMIN_RESETALL, AT);

        assertThat(outcome).isEqualTo(new SweepOutcome(2, 0));
        assertThat(balance(ALEX, GEMS)).isEqualByComparingTo("0");
        assertThat(wallets.findByOwner(ALEX).orElseThrow().balances()).doesNotContainKey(COINS);
        assertThat(ledger()).hasSize(2);
    }

    @Test
    void aSweepThatFailsPartWayChangesNothingAtAll() {
        insertOwners(JooqAccountSweep.CHUNK + 5);
        AtomicInteger chunks = new AtomicInteger();
        JooqAccountSweep failing = new JooqAccountSweep(dsl, count -> {
            if (chunks.incrementAndGet() == 2) {
                throw new IllegalStateException("the server stopped");
            }
            return telemetry.reserveIds(count);
        });

        assertThatThrownBy(() -> failing.creditEvery(coins("25"), EconomyReason.ADMIN_GIVEALL, AT))
                .hasMessage("the server stopped");

        assertThat(chunks).hasValue(2);
        assertThat(ledger()).isEmpty();
        assertThat(wallets.top(COINS, 10)).isEmpty();
    }

    @Test
    void aGiveAllOverMoreAccountsThanOneChunkReachesEveryOne() {
        insertOwners(JooqAccountSweep.CHUNK * 2 + 1);

        SweepOutcome outcome = sweep.creditEvery(coins("1"), EconomyReason.ADMIN_GIVEALL, AT);

        assertThat(outcome.changed()).isEqualTo(JooqAccountSweep.CHUNK * 2 + 1);
        assertThat(ledger()).hasSize(JooqAccountSweep.CHUNK * 2 + 1);
    }

    @Test
    void theSweepsLedgerRowsAndTheBufferedOnesNeverShareAnId() {
        telemetry.recordCredit(ALEX.uuid().toString(), coins("1"), EconomyReason.ADMIN_GIVE, AT);
        wallets.ensureOwner(ALEX);
        sweep.creditEvery(coins("2"), EconomyReason.ADMIN_GIVEALL, AT);
        telemetry.recordCredit(ALEX.uuid().toString(), coins("3"), EconomyReason.ADMIN_GIVE, AT);

        telemetry.flush();

        assertThat(ledger()).hasSize(3);
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
            payments.add(CompletableFuture.supplyAsync(() -> {
                await(start);
                try {
                    return wallets.transfer(owners.get(from), owners.get(to), coins("1"))
                                    .isOk()
                            ? new int[] {from, to}
                            : new int[0];
                } catch (RuntimeException rolledBack) {
                    // A database that breaks a lock cycle may pick the payment; it then moved nothing.
                    return new int[0];
                }
            }));
        }
        CompletableFuture<SweepOutcome> giveAll = CompletableFuture.supplyAsync(() -> {
            await(start);
            return sweep.creditEvery(coins("10"), EconomyReason.ADMIN_GIVEALL, AT);
        });
        start.countDown();

        assertThat(giveAll.get(60, TimeUnit.SECONDS)).isEqualTo(new SweepOutcome(accounts, 0));
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
}
