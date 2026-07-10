package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
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

    @Test
    void rejectsNamesShorterThanTheMinimum() {
        String tooShort = "a".repeat(PlayerWarpName.MIN_LENGTH - 1);
        assertThatThrownBy(() -> PlayerWarpName.of(tooShort)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsNameAtTheMinimumBoundary() {
        String atMinimum = "abc";
        assertThat(PlayerWarpName.of(atMinimum).value()).hasSize(PlayerWarpName.MIN_LENGTH);
    }

    @Test
    void acceptsDigitsUnderscoreAndHyphen() {
        assertThat(PlayerWarpName.of("my_warp-1").value()).isEqualTo("my_warp-1");
    }

    @Test
    void rejectsPunctuationAndSpaces() {
        assertThatThrownBy(() -> PlayerWarpName.of("my warp")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlayerWarpName.of("base!")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canonicalConstructorRejectsUppercaseSoItCannotBeBypassed() {
        assertThatThrownBy(() -> new PlayerWarpName("Base")).isInstanceOf(IllegalArgumentException.class);
    }

    @Property
    void roundTripsAnyWellFormedName(@ForAll("wellFormedNames") String name) {
        assertThat(PlayerWarpName.of(name).value()).isEqualTo(name);
    }

    @Property
    void rejectsAnyNameWithForbiddenCharacters(@ForAll("namesWithForbiddenCharacters") String name) {
        assertThatThrownBy(() -> PlayerWarpName.of(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @Provide
    Arbitrary<String> wellFormedNames() {
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyz0123456789_-")
                .ofMinLength(PlayerWarpName.MIN_LENGTH)
                .ofMaxLength(PlayerWarpName.MAX_LENGTH);
    }

    @Provide
    Arbitrary<String> namesWithForbiddenCharacters() {
        // Every character here survives strip()/toLowerCase() and is outside the [a-z0-9_-] charset, so any
        // generated string is guaranteed to be rejected on shape (internal spaces are not trimmed).
        return Arbitraries.strings()
                .withChars("!@#$%^&*/\\.+=~:; ")
                .ofMinLength(PlayerWarpName.MIN_LENGTH)
                .ofMaxLength(PlayerWarpName.MAX_LENGTH);
    }
}
