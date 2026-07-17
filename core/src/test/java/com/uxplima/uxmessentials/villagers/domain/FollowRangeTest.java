package com.uxplima.uxmessentials.villagers.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * Pins the pure follow-range decision: a villager moves toward its owner only while in the same world and within the
 * range, holds still once out of range or in another world, and the range must be a strictly positive, finite number.
 */
class FollowRangeTest {

    private static final FollowRange RANGE = new FollowRange(16.0);

    @Test
    void movesWhenSameWorldAndInsideTheRange() {
        assertThat(RANGE.shouldMove(true, 100.0)).isTrue();
    }

    @Test
    void movesAtExactlyTheRangeBoundary() {
        assertThat(RANGE.shouldMove(true, RANGE.rangeSquared())).isTrue();
    }

    @Test
    void holdsStillWhenBeyondTheRange() {
        assertThat(RANGE.shouldMove(true, RANGE.rangeSquared() + 1.0)).isFalse();
    }

    @Test
    void holdsStillWhenInAnotherWorldEvenIfClose() {
        assertThat(RANGE.shouldMove(false, 1.0)).isFalse();
    }

    @Test
    void rangeSquaredIsTheSquareOfTheRange() {
        assertThat(RANGE.rangeSquared()).isEqualTo(256.0);
    }

    @Test
    void rejectsANonPositiveOrNonFiniteRange() {
        assertThatIllegalArgumentException().isThrownBy(() -> new FollowRange(0.0));
        assertThatIllegalArgumentException().isThrownBy(() -> new FollowRange(-4.0));
        assertThatIllegalArgumentException().isThrownBy(() -> new FollowRange(Double.NaN));
        assertThatIllegalArgumentException().isThrownBy(() -> new FollowRange(Double.POSITIVE_INFINITY));
    }

    @Test
    void rejectsANegativeDistance() {
        assertThatIllegalArgumentException().isThrownBy(() -> RANGE.shouldMove(true, -1.0));
    }
}
