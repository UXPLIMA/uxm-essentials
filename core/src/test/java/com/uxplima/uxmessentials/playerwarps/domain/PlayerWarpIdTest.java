package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlayerWarpIdTest {

    @Test
    void carriesThePositiveValue() {
        assertThat(PlayerWarpId.of(42L).value()).isEqualTo(42L);
    }

    @Test
    void rejectsZero() {
        assertThatThrownBy(() -> PlayerWarpId.of(0L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeValues() {
        assertThatThrownBy(() -> PlayerWarpId.of(-1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canonicalConstructorRejectsNonPositiveSoItCannotBeBypassed() {
        assertThatThrownBy(() -> new PlayerWarpId(0L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalsByValue() {
        assertThat(PlayerWarpId.of(7L)).isEqualTo(PlayerWarpId.of(7L));
    }
}
