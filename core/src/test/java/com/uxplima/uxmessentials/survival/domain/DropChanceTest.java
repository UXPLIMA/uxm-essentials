package com.uxplima.uxmessentials.survival.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * Pins the pure head-drop roll: the extremes short-circuit (zero never, one hundred always), and a mid chance drops
 * exactly when the bounded draw lands below its resolved threshold.
 */
class DropChanceTest {

    @Test
    void aZeroChanceNeverDrops() {
        DropChance never = new DropChance(0.0);

        assertThat(never.isNever()).isTrue();
        assertThat(never.drops(0)).isFalse();
        assertThat(never.drops(DropChance.RESOLUTION - 1)).isFalse();
    }

    @Test
    void aFullChanceAlwaysDrops() {
        DropChance always = new DropChance(100.0);

        assertThat(always.isAlways()).isTrue();
        assertThat(always.drops(0)).isTrue();
        assertThat(always.drops(DropChance.RESOLUTION - 1)).isTrue();
    }

    @Test
    void aHalfChanceDropsBelowItsThresholdAndNotAtIt() {
        // 50% resolves to a threshold of 5000 basis points: a draw below drops, a draw at or above does not.
        DropChance half = new DropChance(50.0);

        assertThat(half.drops(0)).isTrue();
        assertThat(half.drops(4999)).isTrue();
        assertThat(half.drops(5000)).isFalse();
        assertThat(half.drops(DropChance.RESOLUTION - 1)).isFalse();
    }

    @Test
    void aFractionalChanceResolvesToHundredthsOfAPercent() {
        // 12.50% resolves to 1250 basis points.
        DropChance small = new DropChance(12.5);

        assertThat(small.drops(1249)).isTrue();
        assertThat(small.drops(1250)).isFalse();
    }

    @Test
    void anOutOfRangePercentIsClampedNotRejected() {
        assertThat(new DropChance(150.0).isAlways()).isTrue();
        assertThat(new DropChance(-5.0).isNever()).isTrue();
    }

    @Test
    void rejectsADrawOutsideTheResolution() {
        DropChance half = new DropChance(50.0);

        assertThatIllegalArgumentException().isThrownBy(() -> half.drops(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> half.drops(DropChance.RESOLUTION));
    }
}
