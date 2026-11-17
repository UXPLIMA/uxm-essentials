package com.uxplima.uxmessentials.security.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class PinPolicyTest {

    private final PinPolicy policy = new PinPolicy(6, 8, Set.of("123456"));

    @Test
    void anAsciiPinWithinTheRangePasses() {
        assertThat(policy.validate("482916")).isEqualTo(PinValidation.OK);
    }

    @Test
    void fullwidthDigitsAreNotDigitsTheKeypadCanEnter() {
        assertThat(policy.validate("４８２９１６")).isEqualTo(PinValidation.NOT_NUMERIC);
    }

    @Test
    void aBlockedPinWrittenInFullwidthDigitsDoesNotSlipPastTheBlockedList() {
        assertThat(policy.validate("１２３４５６")).isNotEqualTo(PinValidation.OK);
    }

    @Test
    void arabicIndicDigitsAreNotDigitsTheKeypadCanEnter() {
        assertThat(policy.validate("٤٨٢٩١٦")).isEqualTo(PinValidation.NOT_NUMERIC);
    }
}
