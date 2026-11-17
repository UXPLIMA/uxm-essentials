package com.uxplima.uxmessentials.security.application;

import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private final AttemptLimiter survival = new AttemptLimiter(shared, sessions::revoke, new LockoutPolicy(3), LOCKOUT);
    private final AttemptLimiter lobby = new AttemptLimiter(shared, sessions::revoke, new LockoutPolicy(3), LOCKOUT);

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
    void aProofThatCannotBeReadGivesItsAttemptBackAndIsPassedOn() {
        wrong(survival, NOW);

        for (int i = 0; i < AttemptLimiter.REFUNDS_PER_WINDOW; i++) {
            assertThatThrownBy(() -> survival.check(ADA, NOW, () -> {
                        throw new IllegalStateException("database down");
                    }))
                    .isInstanceOf(IllegalStateException.class);
        }

        assertThat(lobby.check(ADA, NOW, () -> () -> false))
                .as("the outage spent nothing: one wrong proof before it, this is the second")
                .isEqualTo(new AttemptLimiter.Checked(AttemptLimiter.Verdict.WRONG, 1));
    }

    @Test
    void onlyAFewAttemptsAreGivenBackInOneLockoutWindow() {
        for (int i = 0; i < AttemptLimiter.REFUNDS_PER_WINDOW + 1; i++) {
            assertThatThrownBy(() -> survival.check(ADA, NOW, () -> {
                        throw new IllegalStateException("database down");
                    }))
                    .isInstanceOf(IllegalStateException.class);
        }

        assertThat(survival.check(ADA, NOW, () -> () -> false))
                .as("the outage past the refunds counted once")
                .isEqualTo(new AttemptLimiter.Checked(AttemptLimiter.Verdict.WRONG, 1));
        assertThatThrownBy(() -> survival.check(ADA, NOW.plus(LOCKOUT), () -> {
                    throw new IllegalStateException("database down");
                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(survival.check(ADA, NOW.plus(LOCKOUT), () -> () -> false))
                .as("a new window gives back again: this is the third wrong proof, not the fourth")
                .isEqualTo(AttemptLimiter.Checked.LOCKED_NOW);
    }

    @Test
    void aComparisonThatFailsCountsAsAWrongProof() {
        assertThat(survival.check(ADA, NOW, () -> () -> {
                    throw new IllegalStateException("the spent step could not be written");
                }))
                .isEqualTo(new AttemptLimiter.Checked(AttemptLimiter.Verdict.WRONG, 2));
    }

    @Test
    void theProofThatLocksTheAccountIsToldApartFromOneThatFindsItLocked() {
        wrong(survival, NOW);
        wrong(survival, NOW);

        assertThat(survival.check(ADA, NOW, () -> () -> false)).isEqualTo(AttemptLimiter.Checked.LOCKED_NOW);
        assertThat(lobby.check(ADA, NOW, () -> () -> true)).isEqualTo(AttemptLimiter.Checked.ALREADY_LOCKED);
    }

    @Test
    void anAttemptCountedButNeverCheckedIsGivenBack() {
        FailingLockRead store = new FailingLockRead();
        AttemptLimiter limiter = new AttemptLimiter(store, player -> {}, new LockoutPolicy(3), LOCKOUT);
        store.failAfterCount = true;

        assertThatThrownBy(() -> limiter.begin(ADA, NOW)).isInstanceOf(IllegalStateException.class);

        store.failAfterCount = false;
        assertThat(limiter.begin(ADA, NOW).count()).isEqualTo(1);
    }

    @Test
    void aProofSubmittedWhileAnotherIsCheckedOnThisServerIsNotChecked() {
        AttemptLimiter.Checked[] inner = new AttemptLimiter.Checked[1];

        AttemptLimiter.Checked outer = survival.check(ADA, NOW, () -> () -> {
            inner[0] = survival.check(ADA, NOW, () -> () -> true);
            return false;
        });

        assertThat(inner[0]).isEqualTo(AttemptLimiter.Checked.BUSY);
        assertThat(outer).isEqualTo(new AttemptLimiter.Checked(AttemptLimiter.Verdict.WRONG, 2));
        assertThat(survival.check(ADA, NOW, () -> () -> true)).isEqualTo(AttemptLimiter.Checked.PASSED);
    }

    /** The shared record, whose lockout read fails right after a failure was counted, when told to. */
    private static final class FailingLockRead extends MemoryAttemptStoreDelegate {

        boolean failAfterCount;
        private boolean counted;

        @Override
        public synchronized int addFailure(UUID player) {
            counted = true;
            return super.addFailure(player);
        }

        @Override
        public Optional<java.time.Instant> lockedUntil(UUID player) {
            if (failAfterCount && counted) {
                throw new IllegalStateException("database down");
            }
            return super.lockedUntil(player);
        }
    }

    /** Forwards to a {@link MemoryAttemptStore}, so a test may change one call. */
    private static class MemoryAttemptStoreDelegate
            implements com.uxplima.uxmessentials.security.application.port.AttemptStore {

        private final MemoryAttemptStore inner = new MemoryAttemptStore();

        @Override
        public Optional<java.time.Instant> lockedUntil(UUID player) {
            return inner.lockedUntil(player);
        }

        @Override
        public synchronized int addFailure(UUID player) {
            return inner.addFailure(player);
        }

        @Override
        public void lock(UUID player, java.time.Instant until) {
            inner.lock(player, until);
        }

        @Override
        public void clearFailures(UUID player) {
            inner.clearFailures(player);
        }

        @Override
        public void refundFailure(UUID player) {
            inner.refundFailure(player);
        }

        @Override
        public void clearExpired(UUID player, java.time.Instant now) {
            inner.clearExpired(player, now);
        }

        @Override
        public boolean clear(UUID player, java.time.Instant now) {
            return inner.clear(player, now);
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
