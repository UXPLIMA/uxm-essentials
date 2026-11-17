package com.uxplima.uxmessentials.persistence.economy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.uxplima.uxmessentials.economy.application.port.PendingTransactionRepository.PendingTransaction;
import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.ExecuteType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Queued credits through the real migrations: each row is paid by exactly one take. */
@EveryBackend
class JooqPendingTransactionRepositoryTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final PlayerRef ALEX_REF = new PlayerRef(ALEX, "Alex");

    private DSLContext dsl;
    private JooqPendingTransactionRepository repository;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
        repository = new JooqPendingTransactionRepository(dsl);
    }

    @Test
    void aTakeReturnsTheQueuedRowsOnce() {
        repository.queueCredit(ALEX_REF, "coins", new BigDecimal("5"));
        repository.queueCredit(ALEX_REF, "gems", new BigDecimal("2"));

        assertThat(amounts(repository.getAndClearPending(ALEX))).containsExactlyInAnyOrder("coins:5", "gems:2");
        assertThat(repository.getAndClearPending(ALEX)).isEmpty();
    }

    @Test
    void aTakeLeavesTheCurrenciesItIsToldToLeaveOnTheQueue() {
        repository.queueCredit(ALEX_REF, "Coins", new BigDecimal("5"));
        repository.queueCredit(ALEX_REF, "gems", new BigDecimal("2"));

        assertThat(amounts(repository.getAndClearPending(ALEX, Set.of("coins"))))
                .containsExactly("gems:2");
        assertThat(amounts(repository.getAndClearPending(ALEX))).containsExactly("Coins:5");
    }

    @Test
    void aTakeThatRunsBetweenAnotherTakesReadAndDeleteGetsEachRowOnlyOnce() {
        repository.queueCredit(ALEX_REF, "coins", new BigDecimal("5"));
        repository.queueCredit(ALEX_REF, "coins", new BigDecimal("7"));
        List<PendingTransaction> second = new ArrayList<>();
        JooqPendingTransactionRepository first =
                afterFirstRead(() -> second.addAll(repository.getAndClearPending(ALEX)));

        List<PendingTransaction> firstTake = first.getAndClearPending(ALEX);

        assertThat(amounts(second)).containsExactlyInAnyOrder("coins:5", "coins:7");
        assertThat(firstTake).isEmpty();
        assertThat(repository.getAndClearPending(ALEX)).isEmpty();
    }

    @Test
    void twoTakesAtOnceNeverReturnTheSameRowTwice() throws Exception {
        for (int i = 1; i <= 20; i++) {
            repository.queueCredit(ALEX_REF, "coins", BigDecimal.valueOf(i));
        }
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<List<PendingTransaction>> left = CompletableFuture.supplyAsync(() -> take(start));
        CompletableFuture<List<PendingTransaction>> right = CompletableFuture.supplyAsync(() -> take(start));
        start.countDown();

        List<String> ids = new ArrayList<>();
        left.get(30, TimeUnit.SECONDS).forEach(row -> ids.add(row.id()));
        right.get(30, TimeUnit.SECONDS).forEach(row -> ids.add(row.id()));

        assertThat(ids).hasSize(20).doesNotHaveDuplicates();
        assertThat(repository.getAndClearPending(ALEX)).isEmpty();
    }

    @Test
    void aCreditQueuedDuringATakeStaysQueuedForTheNextTake() {
        repository.queueCredit(ALEX_REF, "coins", new BigDecimal("5"));
        JooqPendingTransactionRepository taking =
                afterFirstRead(() -> repository.queueCredit(ALEX_REF, "coins", new BigDecimal("9")));

        assertThat(amounts(taking.getAndClearPending(ALEX))).containsExactly("coins:5");
        assertThat(amounts(repository.getAndClearPending(ALEX))).containsExactly("coins:9");
    }

    private List<PendingTransaction> take(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return repository.getAndClearPending(ALEX);
    }

    /**
     * A repository that runs {@code between} once, right after its first read has finished and before it deletes
     * anything: the window in which another server or a quick rejoin can act on the same rows.
     */
    private JooqPendingTransactionRepository afterFirstRead(Runnable between) {
        AtomicBoolean done = new AtomicBoolean();
        ExecuteListener hook = new ExecuteListener() {
            @Override
            public void end(ExecuteContext ctx) {
                if (ctx.type() == ExecuteType.READ && done.compareAndSet(false, true)) {
                    between.run();
                }
            }
        };
        return new JooqPendingTransactionRepository(
                dsl.configuration().deriveAppending(hook).dsl());
    }

    private static List<String> amounts(List<PendingTransaction> rows) {
        return rows.stream()
                .map(row -> row.currencyId() + ":"
                        + row.amount().stripTrailingZeros().toPlainString())
                .toList();
    }
}
