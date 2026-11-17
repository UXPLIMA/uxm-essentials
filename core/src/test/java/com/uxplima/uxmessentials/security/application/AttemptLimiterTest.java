package com.uxplima.uxmessentials.security.application;

import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.uxplima.uxmessentials.security.domain.LockoutPolicy;
import com.uxplima.uxmessentials.testing.MemoryAttemptStore;
import com.uxplima.uxmessentials.testing.MemoryVerifiedSessions;
import org.junit.jupiter.api.Test;

/**
 * Two backends sharing one attempt record: a wrong proof on either counts toward one budget, the lockout it ends in
 * holds on both, and a success or a staff unlock on either starts the budget again everywhere. An attempt is counted
 * before its proof is checked, so attempts made at once never check more proofs than the budget allows.
 */
class AttemptLimiterTest {

    private static final UUID ADA = new UUID(0L, 7L);
    private static final Duration LOCKOUT = Duration.ofMinutes(5);

    private final MemoryAttemptStore shared = new MemoryAttemptStore();
    private final MemoryVerifiedSessions sessions = new MemoryVerifiedSessions();
    private final AttemptLimiter survival = new AttemptLimiter(shared, sessions, new LockoutPolicy(3), LOCKOUT);
    private final AttemptLimiter lobby = new AttemptLimiter(shared, sessions, new LockoutPolicy(3), LOCKOUT);

    @Test
    void hoppingBetweenBackendsDoesNotBuyMoreAttempts() {
        assertThat(wrong(survival, NOW)).isEqualTo(new AttemptLimiter.Outcome(false, 2));
        assertThat(wrong(lobby, NOW)).isEqualTo(new AttemptLimiter.Outcome(false, 1));

        AttemptLimiter.Outcome third = wrong(survival, NOW);

        assertThat(third.lockedOut()).isTrue();
        assertThat(lobby.isLockedOut(ADA, NOW)).isTrue();
        assertThat(survival.isLockedOut(ADA, NOW)).isTrue();
    }

    @Test
    void aLockoutSetOnOneBackendIsKnownThereWithoutAReadAndOnTheOtherOnceItReadIt() {
        lockOut(survival, NOW);

        assertThat(survival.knownLockedOut(ADA, NOW)).isTrue();
        assertThat(lobby.knownLockedOut(ADA, NOW)).isFalse();
        assertThat(lobby.isLockedOut(ADA, NOW)).isTrue();
        assertThat(lobby.knownLockedOut(ADA, NOW)).isTrue();
    }

    @Test
    void whileLockedOutNoAttemptIsLetThrough() {
        lockOut(survival, NOW);

        assertThat(lobby.begin(ADA, NOW.plusSeconds(1)).refused()).isTrue();
    }

    @Test
    void anExpiredLockoutStartsAFreshBudgetOnEveryBackend() {
        lockOut(survival, NOW);
        assertThat(lobby.isLockedOut(ADA, NOW.plus(LOCKOUT))).isFalse();

        assertThat(survival.isLockedOut(ADA, NOW.plus(LOCKOUT))).isFalse();
        assertThat(wrong(survival, NOW.plus(LOCKOUT))).isEqualTo(new AttemptLimiter.Outcome(false, 2));
    }

    @Test
    void aSuccessOnOneBackendStartsTheBudgetAgainOnTheOther() {
        wrong(survival, NOW);
        wrong(survival, NOW);

        AttemptLimiter.Attempt attempt = lobby.begin(ADA, NOW);
        assertThat(attempt.refused()).isFalse();
        assertThat(lobby.succeeded(ADA, NOW)).isTrue();

        assertThat(wrong(survival, NOW)).isEqualTo(new AttemptLimiter.Outcome(false, 2));
    }

    @Test
    void aRightProofCheckedWhileAParallelAttemptLockedTheAccountIsNoPass() {
        AttemptLimiter.Attempt right = survival.begin(ADA, NOW);
        lockOut(lobby, NOW);

        assertThat(right.refused()).isFalse();
        assertThat(survival.succeeded(ADA, NOW)).isFalse();
    }

    @Test
    void aWrongProofCheckedWhileAParallelAttemptLockedTheAccountReadsAsTheLockoutToo() {
        AttemptLimiter.Attempt wrong = survival.begin(ADA, NOW);
        lockOut(lobby, NOW);

        assertThat(survival.failed(ADA, wrong, NOW).lockedOut()).isTrue();
    }

    @Test
    void attemptsBegunAtOnceCheckNoMoreProofsThanTheBudget() throws Exception {
        int racers = 24;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AttemptLimiter.Attempt>> begun = new ArrayList<>();
        try {
            for (int i = 0; i < racers; i++) {
                AttemptLimiter limiter = i % 2 == 0 ? survival : lobby;
                begun.add(pool.submit(() -> {
                    start.await();
                    return limiter.begin(ADA, NOW);
                }));
            }
            start.countDown();
            int checked = 0;
            for (Future<AttemptLimiter.Attempt> attempt : begun) {
                if (!attempt.get(10, TimeUnit.SECONDS).refused()) {
                    checked++;
                }
            }

            assertThat(checked).isLessThanOrEqualTo(3);
            assertThat(survival.isLockedOut(ADA, NOW)).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aLockoutDropsTheProofThePlayerCouldCarryToAnotherBackend() {
        sessions.record(ADA, "lobby", Optional.empty(), Optional.of("ip-hash-home"), NOW);

        lockOut(survival, NOW);

        assertThat(sessions.find(ADA)).isEmpty();
    }

    @Test
    void aStaffUnlockOnOneBackendLiftsTheLockoutOnBoth() {
        lockOut(survival, NOW);

        assertThat(lobby.clearLockout(ADA, NOW)).isTrue();

        assertThat(survival.isLockedOut(ADA, NOW)).isFalse();
        assertThat(survival.knownLockedOut(ADA, NOW)).isFalse();
        assertThat(lobby.clearLockout(ADA, NOW)).as("nothing left to lift").isFalse();
    }

    private static AttemptLimiter.Outcome wrong(AttemptLimiter limiter, Instant now) {
        return limiter.failed(ADA, limiter.begin(ADA, now), now);
    }

    private static void lockOut(AttemptLimiter limiter, Instant now) {
        for (int i = 0; i < 3; i++) {
            wrong(limiter, now);
        }
    }
}
