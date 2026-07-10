package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WarpDescriptionTest {

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(WarpDescription.of("  the best shop in town  ").value()).isEqualTo("the best shop in town");
    }

    @Test
    void rejectsBlankInputSoAnEmptyBlurbIsNeverStored() {
        assertThatThrownBy(() -> WarpDescription.of("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOverlongInput() {
        String overlong = "a".repeat(WarpDescription.MAX_LENGTH + 1);
        assertThatThrownBy(() -> WarpDescription.of(overlong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsDescriptionAtTheLengthBoundary() {
        String atLimit = "b".repeat(WarpDescription.MAX_LENGTH);
        assertThat(WarpDescription.of(atLimit).value()).hasSize(WarpDescription.MAX_LENGTH);
    }

    @Test
    void canonicalConstructorAlsoRejectsBlankSoItCannotBeBypassed() {
        assertThatThrownBy(() -> new WarpDescription("")).isInstanceOf(IllegalArgumentException.class);
    }
}
