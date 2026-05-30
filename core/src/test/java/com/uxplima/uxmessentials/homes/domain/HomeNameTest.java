package com.uxplima.uxmessentials.homes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * {@link HomeName} normalisation and validation: trimming, case-folding, and the blank/overlong rejection
 * that keeps an invalid name out of the aggregate.
 */
class HomeNameTest {

    @Test
    void normalisesToTrimmedLowercase() {
        assertThat(HomeName.of("  Base  ").value()).isEqualTo("base");
        assertThat(HomeName.of("HOME").value()).isEqualTo("home");
    }

    @Test
    void twoCasingsOfOneWordAreEqual() {
        assertThat(HomeName.of("Base")).isEqualTo(HomeName.of("BASE"));
    }

    @Test
    void blankInputIsRejected() {
        assertThatThrownBy(() -> HomeName.of("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overlongInputIsRejected() {
        String tooLong = "x".repeat(HomeName.MAX_LENGTH + 1);
        assertThatThrownBy(() -> HomeName.of(tooLong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void limitReachedRespectsTheUnlimitedSentinel() {
        assertThat(HomeLimit.of(3).isReachedAt(3)).isTrue();
        assertThat(HomeLimit.of(3).isReachedAt(2)).isFalse();
        assertThat(HomeLimit.noLimit().isReachedAt(Integer.MAX_VALUE - 1)).isFalse();
    }
}
