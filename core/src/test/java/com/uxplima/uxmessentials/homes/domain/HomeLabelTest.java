package com.uxplima.uxmessentials.homes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** {@link HomeLabel} case preservation, trimming, blank rejection, and length cap. */
class HomeLabelTest {

    @Test
    void caseIsPreserved() {
        assertThat(HomeLabel.of("Base").value()).isEqualTo("Base");
    }

    @Test
    void leadingAndTrailingSpacesAreStripped() {
        assertThat(HomeLabel.of(" Base ").value()).isEqualTo("Base");
    }

    @Test
    void mixedCaseIsKeptExact() {
        assertThat(HomeLabel.of("MyHome1").value()).isEqualTo("MyHome1");
    }

    @Test
    void blankInputIsRejected() {
        assertThatThrownBy(() -> HomeLabel.of("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overlongInputIsRejected() {
        String tooLong = "a".repeat(HomeLabel.MAX_LENGTH + 1);
        assertThatThrownBy(() -> HomeLabel.of(tooLong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exactMaxLengthIsAccepted() {
        String atLimit = "b".repeat(HomeLabel.MAX_LENGTH);
        assertThat(HomeLabel.of(atLimit).value()).isEqualTo(atLimit);
    }
}
