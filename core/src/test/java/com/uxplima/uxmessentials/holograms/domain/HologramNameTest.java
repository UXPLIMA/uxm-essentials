package com.uxplima.uxmessentials.holograms.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HologramNameTest {

    @Test
    void normalisesToTrimmedLowercase() {
        assertThat(HologramName.of("  Spawn ").value()).isEqualTo("spawn");
        assertThat(HologramName.of("SHOP").value()).isEqualTo("shop");
    }

    @Test
    void rejectsBlankInput() {
        assertThatThrownBy(() -> HologramName.of("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOverlongInput() {
        String tooLong = "a".repeat(HologramName.MAX_LENGTH + 1);
        assertThatThrownBy(() -> HologramName.of(tooLong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsNameAtTheMaxLength() {
        String atMax = "a".repeat(HologramName.MAX_LENGTH);
        assertThat(HologramName.of(atMax).value()).hasSize(HologramName.MAX_LENGTH);
    }
}
