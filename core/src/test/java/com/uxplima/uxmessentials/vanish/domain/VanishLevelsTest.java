package com.uxplima.uxmessentials.vanish.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The pure see/use level comparison in isolation: a viewer sees a vanished player iff the viewer's see level is at
 * least the target's use level. Pins the PremiumVanish semantics (higher use hides from lower see; equal or greater
 * see reveals) and the two anchor points (no-see-level 0 never clears; level 1 is the flat default).
 */
class VanishLevelsTest {

    @Test
    void noSeeLevelNeverClearsTheDefaultUseLevel() {
        assertThat(VanishLevels.sees(VanishLevels.NO_SEE_LEVEL, VanishLevel.DEFAULT))
                .isFalse();
    }

    @Test
    void seeLevelOneClearsTheDefaultUseLevel() {
        assertThat(VanishLevels.sees(1, VanishLevel.DEFAULT)).isTrue();
    }

    @Test
    void aHigherUseLevelHidesFromALowerSeeLevel() {
        assertThat(VanishLevels.sees(2, VanishLevel.of(3))).isFalse();
    }

    @Test
    void anEqualSeeLevelReveals() {
        assertThat(VanishLevels.sees(3, VanishLevel.of(3))).isTrue();
    }

    @Test
    void aGreaterSeeLevelReveals() {
        assertThat(VanishLevels.sees(5, VanishLevel.of(3))).isTrue();
    }
}
