package com.uxplima.uxmessentials.homes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** {@link HomeIcon} uppercasing, trimming, blank rejection, and null safety. */
class HomeIconTest {

    @Test
    void materialNameIsUpperCased() {
        assertThat(HomeIcon.of("end_rod").materialName()).isEqualTo("END_ROD");
    }

    @Test
    void alreadyUpperCasedInputIsAccepted() {
        assertThat(HomeIcon.of("GRASS_BLOCK").materialName()).isEqualTo("GRASS_BLOCK");
    }

    @Test
    void leadingAndTrailingSpacesAreStripped() {
        assertThat(HomeIcon.of("  stone  ").materialName()).isEqualTo("STONE");
    }

    @Test
    void emptyStringIsRejected() {
        assertThatThrownBy(() -> HomeIcon.of("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankStringIsRejected() {
        assertThatThrownBy(() -> HomeIcon.of("   ")).isInstanceOf(IllegalArgumentException.class);
    }
}
