package com.uxplima.uxmessentials.security.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The same-IP account cap: a zero/negative cap never denies, and a positive cap denies once the count exceeds it. */
class AltLimitPolicyTest {

    @Test
    void aZeroCapIsUnlimited() {
        AltLimitPolicy policy = new AltLimitPolicy(0);

        assertThat(policy.unlimited()).isTrue();
        assertThat(policy.evaluate(50)).isEqualTo(AltLimitPolicy.Decision.ALLOW);
    }

    @Test
    void aNegativeCapIsUnlimited() {
        assertThat(new AltLimitPolicy(-3).evaluate(10)).isEqualTo(AltLimitPolicy.Decision.ALLOW);
    }

    @Test
    void withinTheCapIsAllowed() {
        AltLimitPolicy policy = new AltLimitPolicy(2);

        assertThat(policy.evaluate(1)).isEqualTo(AltLimitPolicy.Decision.ALLOW);
        assertThat(policy.evaluate(2)).isEqualTo(AltLimitPolicy.Decision.ALLOW);
    }

    @Test
    void oneOverTheCapIsDenied() {
        AltLimitPolicy policy = new AltLimitPolicy(2);

        assertThat(policy.evaluate(3)).isEqualTo(AltLimitPolicy.Decision.DENY);
    }
}
