package com.uxplima.uxmessentials.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Pins {@link PinPolicy}: the numeric-only, length-bounded format check and its typed rejection reasons. */
class PinPolicyTest {

    private final PinPolicy policy = new PinPolicy(4, 8);

    @Test
    void acceptsANumericPinWithinTheLengthRange() {
        assertThat(policy.validate("1234")).isEqualTo(PinValidation.OK);
        assertThat(policy.validate("12345678")).isEqualTo(PinValidation.OK);
    }

    @Test
    void rejectsAPinShorterThanTheMinimum() {
        assertThat(policy.validate("123")).isEqualTo(PinValidation.TOO_SHORT);
    }

    @Test
    void rejectsAPinLongerThanTheMaximum() {
        assertThat(policy.validate("123456789")).isEqualTo(PinValidation.TOO_LONG);
    }

    @Test
    void rejectsANonNumericPinBeforeMeasuringItsLength() {
        assertThat(policy.validate("12a4")).isEqualTo(PinValidation.NOT_NUMERIC);
        assertThat(policy.validate("")).isEqualTo(PinValidation.NOT_NUMERIC);
    }

    @Test
    void rejectsAnInvalidLengthRangeAtConstruction() {
        assertThatThrownBy(() -> new PinPolicy(0, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PinPolicy(6, 4)).isInstanceOf(IllegalArgumentException.class);
    }
}
