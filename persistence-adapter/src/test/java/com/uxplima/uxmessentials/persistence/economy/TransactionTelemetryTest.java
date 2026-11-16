package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Transactions.TRANSACTIONS;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
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
 * Two servers writing the ledger of one database never write the same id, and a batch that meets an id somebody else
 * wrote is dropped and logged instead of being retried for ever.
 */
@EveryBackend
class TransactionTelemetryTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final long AT = 1_900_000_000_000L;

    private DSLContext dsl;
    private final RecordingLogger log = new RecordingLogger();
    private TransactionTelemetry survival;
    private TransactionTelemetry lobby;
    private Executor servers = ForkJoinPool.commonPool();

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
        if (database.backend().isFile()) {
            // The plugin holds one connection to SQLite, so its writers never overlap; the test's plain data source
            // would open a connection per thread and contend for the file instead.
            servers = Executors.newSingleThreadExecutor();
        }
        survival = telemetry();
        lobby = telemetry();
    }

    @Test
    void twoServersFlushingInTurnBothKeepEveryRow() {
        credit(survival, 3);
        credit(lobby, 4);
        survival.flush();
        lobby.flush();
        credit(survival, 2);
        survival.flush();

        assertThat(ids()).hasSize(9).doesNotHaveDuplicates();
        assertThat(log.errors).isEmpty();
    }

    @Test
    void twoServersFlushingAtOnceBothKeepEveryRow() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Void>> flushes = new ArrayList<>();
        for (TransactionTelemetry server : List.of(survival, lobby)) {
            flushes.add(CompletableFuture.runAsync(
                    () -> {
                        await(start);
                        for (int round = 0; round < 10; round++) {
                            credit(server, 25);
                            server.flush();
                        }
                    },
                    servers));
        }
        start.countDown();
        for (CompletableFuture<Void> flush : flushes) {
            flush.get(60, TimeUnit.SECONDS);
        }

        assertThat(ids()).hasSize(500).doesNotHaveDuplicates();
    }

    @Test
    void blocksReservedOnTwoServersAtOnceNeverOverlap() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<List<Long>>> reservations = new ArrayList<>();
        for (TransactionTelemetry server : List.of(survival, lobby, survival, lobby)) {
            reservations.add(CompletableFuture.supplyAsync(
                    () -> {
                        await(start);
                        List<Long> firsts = new ArrayList<>();
                        for (int i = 0; i < 20; i++) {
                            firsts.add(server.reserveIds(10));
                        }
                        return firsts;
                    },
                    servers));
        }
        start.countDown();

        Set<Long> taken = new HashSet<>();
        for (CompletableFuture<List<Long>> reservation : reservations) {
            for (long first : reservation.get(60, TimeUnit.SECONDS)) {
                for (long id = first; id < first + 10; id++) {
                    assertThat(taken.add(id)).as("id %d handed out twice", id).isTrue();
                }
            }
        }
        assertThat(taken).hasSize(800);
    }

    @Test
    void aBatchMeetingIdsAnotherWriterTookIsDroppedAndLoggedAndTheNextOneLands() {
        long next = survival.reserveIds(1) + 1;
        // A server on an older release counts ids itself and writes the next two the allocator would hand out.
        writeRow(next);
        writeRow(next + 1);
        credit(survival, 1);

        survival.flush();

        assertThat(log.errors).singleElement().asString().contains("event=ledger_flush_dropped rows=1");
        credit(survival, 1);
        survival.flush();
        assertThat(ids()).hasSize(3).doesNotHaveDuplicates();
        assertThat(log.errors).hasSize(1);
    }

    private TransactionTelemetry telemetry() {
        TransactionTelemetry telemetry = new TransactionTelemetry(dsl, new IdleScheduler(), log, Duration.ofHours(1));
        telemetry.start();
        return telemetry;
    }

    private static void credit(TransactionTelemetry server, int rows) {
        for (int i = 0; i < rows; i++) {
            server.recordCredit(
                    new PlayerRef(new java.util.UUID(0L, i + 1L), "P" + i)
                            .uuid()
                            .toString(),
                    Money.of(COINS, BigDecimal.ONE),
                    EconomyReason.ADMIN_GIVE,
                    AT);
        }
    }

    private void writeRow(long id) {
        dsl.transaction(configuration -> configuration
                .dsl()
                .insertInto(TRANSACTIONS)
                .set(TRANSACTIONS.ID, id)
                .set(TRANSACTIONS.TS, AT)
                .set(TRANSACTIONS.TO_OWNER, "00000000-0000-0000-0000-000000000001")
                .set(TRANSACTIONS.CURRENCY, "coins")
                .set(TRANSACTIONS.AMOUNT, BigDecimal.ONE)
                .set(TRANSACTIONS.TYPE, "CREDIT")
                .set(TRANSACTIONS.REASON, "ADMIN_GIVE")
                .execute());
    }

    private List<Long> ids() {
        return dsl.select(TRANSACTIONS.ID).from(TRANSACTIONS).fetch(TRANSACTIONS.ID);
    }

    private static void await(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Never runs the flush loop: a test flushes by hand. */
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
