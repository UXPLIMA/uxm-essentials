package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlayerWarpNameTest {

    @Test
    void normalisesToLowercaseAndTrims() {
        assertThat(PlayerWarpName.of("  Base  ").value()).isEqualTo("base");
    }

    @Test
    void rejectsBlankInput() {
        assertThatThrownBy(() -> PlayerWarpName.of("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOverlongInput() {
        String overlong = "a".repeat(PlayerWarpName.MAX_LENGTH + 1);
        assertThatThrownBy(() -> PlayerWarpName.of(overlong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsNameAtTheLengthBoundary() {
        String atLimit = "b".repeat(PlayerWarpName.MAX_LENGTH);
        assertThat(PlayerWarpName.of(atLimit).value()).hasSize(PlayerWarpName.MAX_LENGTH);
    }
}
