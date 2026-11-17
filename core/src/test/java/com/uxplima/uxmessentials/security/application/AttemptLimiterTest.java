package com.uxplima.uxmessentials.security.application;

import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import com.uxplima.uxmessentials.security.domain.LockoutPolicy;
import com.uxplima.uxmessentials.testing.MemoryAttemptStore;
import org.junit.jupiter.api.Test;

/**
 * Two backends sharing one attempt record: a wrong proof on either counts toward one budget, the lockout it ends in
 * holds on both, and a success or a staff unlock on either starts the budget again everywhere.
 */
class AttemptLimiterTest {

    private static final UUID ADA = new UUID(0L, 7L);
    private static final Duration LOCKOUT = Duration.ofMinutes(5);

    private final MemoryAttemptStore shared = new MemoryAttemptStore();
    private final AttemptLimiter survival = new AttemptLimiter(shared, new LockoutPolicy(3), LOCKOUT);
    private final AttemptLimiter lobby = new AttemptLimiter(shared, new LockoutPolicy(3), LOCKOUT);

    @Test
    void hoppingBetweenBackendsDoesNotBuyMoreAttempts() {
        assertThat(survival.recordFailure(ADA, NOW)).isEqualTo(new AttemptLimiter.Outcome(false, 2));
        assertThat(lobby.recordFailure(ADA, NOW)).isEqualTo(new AttemptLimiter.Outcome(false, 1));

        AttemptLimiter.Outcome third = survival.recordFailure(ADA, NOW);

        assertThat(third.lockedOut()).isTrue();
        assertThat(lobby.isLockedOut(ADA, NOW)).isTrue();
        assertThat(survival.isLockedOut(ADA, NOW)).isTrue();
    }

    @Test
    void aLockoutSetOnOneBackendIsKnownThereWithoutAReadAndOnTheOtherOnceItReadIt() {
        survival.recordFailure(ADA, NOW);
        survival.recordFailure(ADA, NOW);
        survival.recordFailure(ADA, NOW);

        assertThat(survival.knownLockedOut(ADA, NOW)).isTrue();
        assertThat(lobby.knownLockedOut(ADA, NOW)).isFalse();
        assertThat(lobby.isLockedOut(ADA, NOW)).isTrue();
        assertThat(lobby.knownLockedOut(ADA, NOW)).isTrue();
    }

    @Test
    void anExpiredLockoutStartsAFreshBudgetOnEveryBackend() {
        for (int i = 0; i < 3; i++) {
            survival.recordFailure(ADA, NOW);
        }
        assertThat(lobby.isLockedOut(ADA, NOW.plus(LOCKOUT))).isFalse();

        assertThat(survival.isLockedOut(ADA, NOW.plus(LOCKOUT))).isFalse();
        assertThat(survival.recordFailure(ADA, NOW.plus(LOCKOUT))).isEqualTo(new AttemptLimiter.Outcome(false, 2));
    }

    @Test
    void aSuccessOnOneBackendStartsTheBudgetAgainOnTheOther() {
        survival.recordFailure(ADA, NOW);
        survival.recordFailure(ADA, NOW);

        lobby.recordSuccess(ADA);

        assertThat(survival.recordFailure(ADA, NOW)).isEqualTo(new AttemptLimiter.Outcome(false, 2));
    }

    @Test
    void aStaffUnlockOnOneBackendLiftsTheLockoutOnBoth() {
        for (int i = 0; i < 3; i++) {
            survival.recordFailure(ADA, NOW);
        }

        assertThat(lobby.clearLockout(ADA, NOW)).isTrue();

        assertThat(survival.isLockedOut(ADA, NOW)).isFalse();
        assertThat(survival.knownLockedOut(ADA, NOW)).isFalse();
        assertThat(lobby.clearLockout(ADA, NOW)).as("nothing left to lift").isFalse();
    }
}
