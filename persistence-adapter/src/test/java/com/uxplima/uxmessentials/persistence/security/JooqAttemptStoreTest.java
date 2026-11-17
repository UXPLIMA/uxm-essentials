package com.uxplima.uxmessentials.persistence.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
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

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.security.application.AttemptLimiter;
import com.uxplima.uxmessentials.security.domain.LockoutPolicy;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The attempt record through the real migrations, as two backends on one database use it: each through its own store,
 * one budget and one lockout between them, and failures counted at once are each counted.
 */
@EveryBackend
class JooqAttemptStoreTest {

    private static final UUID ADA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final Duration LOCKOUT = Duration.ofMinutes(5);

    private DSLContext dsl;
    private Executor servers = ForkJoinPool.commonPool();
    private JooqAttemptStore survivalStore;
    private JooqVerifiedSessions sessions;
    private AttemptLimiter survival;
    private AttemptLimiter lobby;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
        if (database.backend().isFile()) {
            // The plugin holds one connection to SQLite, so its writers never overlap; the test's plain data source
            // would open a connection per thread and contend for the file instead.
            servers = Executors.newSingleThreadExecutor();
        }
        survivalStore = new JooqAttemptStore(dsl);
        sessions = new JooqVerifiedSessions(dsl);
        survival = new AttemptLimiter(survivalStore, sessions::revoke, new LockoutPolicy(3), LOCKOUT);
        lobby = new AttemptLimiter(
                new JooqAttemptStore(database.dsl()),
                new JooqVerifiedSessions(database.dsl())::revoke,
                new LockoutPolicy(3),
                LOCKOUT);
    }

    @Test
    void failuresOnTwoBackendsShareOneBudgetAndTheLockoutHoldsOnBoth() {
        sessions.record(ADA, "lobby", Optional.empty(), Optional.of("ip-hash-home"), NOW);
        wrong(survival);
        wrong(lobby);

        assertThat(wrong(survival).lockedOut()).isTrue();

        assertThat(sessions.find(ADA))
                .as("a lockout drops the proof the player could carry")
                .isEmpty();
        assertThat(lobby.isLockedOut(ADA, NOW)).isTrue();
        assertThat(lobby.isLockedOut(ADA, NOW.plus(LOCKOUT))).as("ended").isFalse();
        assertThat(survival.isLockedOut(ADA, NOW.plus(LOCKOUT))).isFalse();
    }

    @Test
    void aSuccessKeepsALockoutAndAnUnlockLiftsIt() {
        survivalStore.lock(ADA, NOW.plus(LOCKOUT));

        assertThat(lobby.begin(ADA, NOW).refused()).isTrue();
        assertThat(lobby.succeeded(ADA, NOW))
                .as("a success does not lift a lockout")
                .isFalse();

        assertThat(survival.isLockedOut(ADA, NOW)).isTrue();
        assertThat(lobby.clearLockout(ADA, NOW)).isTrue();
        assertThat(survival.isLockedOut(ADA, NOW)).isFalse();
        assertThat(lobby.clearLockout(ADA, NOW)).isFalse();
    }

    @Test
    void clearingAnEndedLockoutLeavesOneAnotherBackendSetSinceAndAStaffUnlockLiftsAnyOther() {
        survivalStore.lock(ADA, NOW.plus(LOCKOUT));
        Instant later = NOW.plus(LOCKOUT).plusSeconds(1);
        // Another backend locked the account again after this one read the ended lockout.
        new JooqAttemptStore(dsl).lock(ADA, later.plus(LOCKOUT));

        survivalStore.clearExpired(ADA, later);

        assertThat(survivalStore.lockedUntil(ADA)).contains(later.plus(LOCKOUT));
        assertThat(survivalStore.clear(ADA, later)).isTrue();
        assertThat(survivalStore.lockedUntil(ADA)).isEmpty();
    }

    @Test
    void attemptsBegunAtOnceOnTwoBackendsCheckNoMoreProofsThanTheBudget() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<AttemptLimiter.Attempt>> begun = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            AttemptLimiter backend = i % 2 == 0 ? survival : lobby;
            begun.add(CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            start.await();
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                        return backend.begin(ADA, NOW);
                    },
                    servers));
        }
        start.countDown();

        int checked = 0;
        for (CompletableFuture<AttemptLimiter.Attempt> attempt : begun) {
            if (!attempt.get(30, TimeUnit.SECONDS).refused()) {
                checked++;
            }
        }

        assertThat(checked).isLessThanOrEqualTo(3);
        assertThat(survival.isLockedOut(ADA, NOW)).isTrue();
    }

    @Test
    void aFailureGivenBackIsTakenOffTheCountButNeverBelowZero() {
        survivalStore.addFailure(ADA);
        survivalStore.addFailure(ADA);

        for (int i = 0; i < 3; i++) {
            new JooqAttemptStore(dsl).refundFailure(ADA);
        }

        assertThat(survivalStore.addFailure(ADA)).isEqualTo(1);
    }

    @Test
    void failuresCountedAtOnceAreEachCounted() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Integer>> counts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            JooqAttemptStore backend = i % 2 == 0 ? survivalStore : new JooqAttemptStore(dsl);
            counts.add(CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            start.await();
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                        return backend.addFailure(ADA);
                    },
                    servers));
        }
        start.countDown();

        List<Integer> seen = new ArrayList<>();
        for (CompletableFuture<Integer> count : counts) {
            seen.add(count.get(30, TimeUnit.SECONDS));
        }

        assertThat(seen).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
    }

    private static AttemptLimiter.Outcome wrong(AttemptLimiter limiter) {
        return limiter.failed(ADA, limiter.begin(ADA, NOW), NOW);
    }
}
