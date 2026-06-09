package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.COINS;
import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.CURRENCIES;
import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.coins;
import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.randomPlayer;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankMember;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankRole;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The atomic shared-bank money moves against the default embedded SQLite backend. {@code deposit} guards the
 * wallet debit and adds to the bank in one transaction; {@code withdraw} subtracts from the bank with a guarded
 * {@code UPDATE … WHERE balance >= ?} and credits the wallet in one transaction. The load-bearing case:
 * concurrent withdrawals can never overdraw the bank because the sufficiency check is in SQL, not the JVM.
 */
class JooqBankRepositoryTest {

    private Persistence persistence;
    private JooqBankRepository banks;
    private JooqWalletRepository wallets;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(
                new EconomyTestSupport.SqliteConfig(),
                dataFolder,
                EconomyTestSupport.baselineMigrations(),
                new EconomyTestSupport.NoopLogger());
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        banks = new JooqBankRepository(persistence.dsl(), CURRENCIES, clock);
        wallets = new JooqWalletRepository(persistence.dsl(), CURRENCIES, clock);
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    private SharedBank newBank(String id, PlayerRef leader) {
        wallets.ensureOwner(leader);
        SharedBank bank = new SharedBank(
                id, "Bank " + id, Money.zero(COINS), leader, List.of(new BankMember(leader, BankRole.LEADER)), 0L);
        banks.save(bank);
        return bank;
    }

    @Test
    void depositDebitsTheWalletAndAddsToTheBankAtomically() {
        PlayerRef leader = randomPlayer();
        newBank("alpha", leader);
        wallets.credit(leader, coins(500));

        assertThat(banks.deposit("alpha", leader, coins(200)).isOk()).isTrue();

        assertThat(wallets.findByOwner(leader).orElseThrow().balanceOf(COINS)).isEqualTo(coins(300));
        assertThat(banks.findById("alpha").orElseThrow().balance()).isEqualTo(coins(200));
    }

    @Test
    void depositShortOfFundsLeavesBothSidesUntouched() {
        PlayerRef leader = randomPlayer();
        newBank("beta", leader);
        wallets.credit(leader, coins(50));

        Result<Unit, TransferError> result = banks.deposit("beta", leader, coins(200));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TransferError.INSUFFICIENT_FUNDS);
        assertThat(wallets.findByOwner(leader).orElseThrow().balanceOf(COINS)).isEqualTo(coins(50));
        assertThat(banks.findById("beta").orElseThrow().balance().amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void withdrawSubtractsFromTheBankAndCreditsTheWalletAtomically() {
        PlayerRef leader = randomPlayer();
        newBank("gamma", leader);
        wallets.credit(leader, coins(500));
        banks.deposit("gamma", leader, coins(300));

        assertThat(banks.withdraw("gamma", leader, coins(120)).isOk()).isTrue();

        assertThat(banks.findById("gamma").orElseThrow().balance()).isEqualTo(coins(180));
        assertThat(wallets.findByOwner(leader).orElseThrow().balanceOf(COINS)).isEqualTo(coins(320));
    }

    @Test
    void withdrawBeyondTheBankBalanceLeavesBothSidesUntouched() {
        PlayerRef leader = randomPlayer();
        newBank("delta", leader);
        wallets.credit(leader, coins(500));
        banks.deposit("delta", leader, coins(100));

        Result<Unit, TransferError> result = banks.withdraw("delta", leader, coins(250));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TransferError.INSUFFICIENT_FUNDS);
        assertThat(banks.findById("delta").orElseThrow().balance()).isEqualTo(coins(100));
        assertThat(wallets.findByOwner(leader).orElseThrow().balanceOf(COINS)).isEqualTo(coins(400));
    }

    @RepeatedTest(20)
    void concurrentWithdrawalsNeverOverdrawTheBank() throws Exception {
        PlayerRef leader = randomPlayer();
        newBank("epsilon", leader);
        wallets.credit(leader, coins(1000));
        banks.deposit("epsilon", leader, coins(100));

        AtomicInteger succeeded = runConcurrently(20, () -> banks.withdraw("epsilon", leader, coins(10)));

        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(banks.findById("epsilon").orElseThrow().balance().amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private AtomicInteger runConcurrently(int threads, java.util.function.Supplier<Result<Unit, TransferError>> task)
            throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger succeeded = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        if (task.get().isOk()) {
                            succeeded.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        return succeeded;
    }
}
