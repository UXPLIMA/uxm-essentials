package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class DisplayNameTest {

    @Test
    void trimsButKeepsCaseAndInnerSpaces() {
        assertThat(DisplayName.of("  My Cozy Base  ").value()).isEqualTo("My Cozy Base");
    }

    @Test
    void rejectsBlankInput() {
        assertThatThrownBy(() -> DisplayName.of("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOverlongInput() {
        String overlong = "a".repeat(DisplayName.MAX_LENGTH + 1);
        assertThatThrownBy(() -> DisplayName.of(overlong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsNameAtTheLengthBoundary() {
        String atLimit = "b".repeat(DisplayName.MAX_LENGTH);
        assertThat(DisplayName.of(atLimit).value()).hasSize(DisplayName.MAX_LENGTH);
    }

    @Test
    void toStringRendersTheRawValueForDirectUseInComponents() {
        assertThat(new DisplayName("Spawn Hub").toString()).isEqualTo("Spawn Hub");
    }

    @Test
    void canonicalConstructorAlsoRejectsBlankSoItCannotBeBypassed() {
        assertThatThrownBy(() -> new DisplayName("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Property
    void acceptsAnyLengthUpToTheLimit(@ForAll @IntRange(min = 1, max = DisplayName.MAX_LENGTH) int length) {
        String name = "x".repeat(length);
        assertThat(DisplayName.of(name).value()).hasSize(length);
    }
}
