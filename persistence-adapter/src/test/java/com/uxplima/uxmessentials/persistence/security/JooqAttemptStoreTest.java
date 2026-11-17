package com.uxplima.uxmessentials.persistence.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
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
    private JooqAttemptStore survivalStore;
    private AttemptLimiter survival;
    private AttemptLimiter lobby;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
        survivalStore = new JooqAttemptStore(dsl);
        survival = new AttemptLimiter(survivalStore, new LockoutPolicy(3), LOCKOUT);
        lobby = new AttemptLimiter(new JooqAttemptStore(database.dsl()), new LockoutPolicy(3), LOCKOUT);
    }

    @Test
    void failuresOnTwoBackendsShareOneBudgetAndTheLockoutHoldsOnBoth() {
        survival.recordFailure(ADA, NOW);
        lobby.recordFailure(ADA, NOW);

        assertThat(survival.recordFailure(ADA, NOW).lockedOut()).isTrue();

        assertThat(lobby.isLockedOut(ADA, NOW)).isTrue();
        assertThat(lobby.isLockedOut(ADA, NOW.plus(LOCKOUT))).as("ended").isFalse();
        assertThat(survival.isLockedOut(ADA, NOW.plus(LOCKOUT))).isFalse();
    }

    @Test
    void aSuccessKeepsALockoutAndAnUnlockLiftsIt() {
        survivalStore.lock(ADA, NOW.plus(LOCKOUT));

        lobby.recordSuccess(ADA);

        assertThat(survival.isLockedOut(ADA, NOW)).isTrue();
        assertThat(lobby.clearLockout(ADA, NOW)).isTrue();
        assertThat(survival.isLockedOut(ADA, NOW)).isFalse();
        assertThat(lobby.clearLockout(ADA, NOW)).isFalse();
    }

    @Test
    void failuresCountedAtOnceAreEachCounted() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Integer>> counts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            JooqAttemptStore backend = i % 2 == 0 ? survivalStore : new JooqAttemptStore(dsl);
            counts.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return backend.addFailure(ADA);
            }));
        }
        start.countDown();

        List<Integer> seen = new ArrayList<>();
        for (CompletableFuture<Integer> count : counts) {
            seen.add(count.get(30, TimeUnit.SECONDS));
        }

        assertThat(seen).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
    }
}
