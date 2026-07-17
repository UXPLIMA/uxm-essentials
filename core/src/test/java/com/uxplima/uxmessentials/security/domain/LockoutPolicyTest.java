package com.uxplima.uxmessentials.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uxplima.uxmessentials.security.domain.LockoutPolicy.AttemptOutcome;
import org.junit.jupiter.api.Test;

/** Pins the pure lockout decision: retry until the failure count reaches the limit, then lock out, with the count. */
class LockoutPolicyTest {

    @Test
    void retriesUntilTheLimitIsReachedThenLocksOut() {
        LockoutPolicy policy = new LockoutPolicy(3);

        assertThat(policy.evaluate(1)).isEqualTo(AttemptOutcome.RETRY);
        assertThat(policy.evaluate(2)).isEqualTo(AttemptOutcome.RETRY);
        assertThat(policy.evaluate(3)).isEqualTo(AttemptOutcome.LOCKED_OUT);
        assertThat(policy.evaluate(4)).isEqualTo(AttemptOutcome.LOCKED_OUT);
    }

    @Test
    void reportsTheRemainingAttemptsClampedAtZero() {
        LockoutPolicy policy = new LockoutPolicy(3);

        assertThat(policy.remaining(0)).isEqualTo(3);
        assertThat(policy.remaining(1)).isEqualTo(2);
        assertThat(policy.remaining(3)).isEqualTo(0);
        assertThat(policy.remaining(5)).isEqualTo(0);
    }

    @Test
    void aSingleAttemptPolicyLocksOutOnTheFirstFailure() {
        LockoutPolicy policy = new LockoutPolicy(1);

        assertThat(policy.evaluate(1)).isEqualTo(AttemptOutcome.LOCKED_OUT);
        assertThat(policy.remaining(1)).isEqualTo(0);
    }

    @Test
    void rejectsANonPositiveLimitAndANegativeCount() {
        assertThatThrownBy(() -> new LockoutPolicy(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LockoutPolicy(3).evaluate(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
