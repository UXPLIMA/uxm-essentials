package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class IconSpecTest {

    @Test
    void trimsButLeavesTheSchemeUntouched() {
        assertThat(IconSpec.of("  itemsadder:my_pack:diamond_sword  ").value())
                .isEqualTo("itemsadder:my_pack:diamond_sword");
    }

    @Test
    void keepsAnyOpaqueSchemeVerbatim() {
        assertThat(IconSpec.of("base64:eyJ0ZXh0dXJlcyI6e319").value()).isEqualTo("base64:eyJ0ZXh0dXJlcyI6e319");
        assertThat(IconSpec.of("cmd:1234").value()).isEqualTo("cmd:1234");
    }

    @Test
    void rejectsBlankInput() {
        assertThatThrownBy(() -> IconSpec.of("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOverlongInput() {
        String overlong = "a".repeat(IconSpec.MAX_LENGTH + 1);
        assertThatThrownBy(() -> IconSpec.of(overlong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsTokenAtTheLengthBoundary() {
        String atLimit = "b".repeat(IconSpec.MAX_LENGTH);
        assertThat(IconSpec.of(atLimit).value()).hasSize(IconSpec.MAX_LENGTH);
    }

    @Property
    void acceptsAnyLengthUpToTheLimit(@ForAll @IntRange(min = 1, max = IconSpec.MAX_LENGTH) int length) {
        String token = "x".repeat(length);
        assertThat(IconSpec.of(token).value()).hasSize(length);
    }
}
