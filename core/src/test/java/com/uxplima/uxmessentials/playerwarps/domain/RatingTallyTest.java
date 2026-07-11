package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The raw star totals value object: an empty tally is {@code (0, 0)}, and neither total may be negative. */
class RatingTallyTest {

    @Test
    void emptyIsZeroSumAndZeroCount() {
        RatingTally empty = RatingTally.empty();

        assertThat(empty.sum()).isZero();
        assertThat(empty.count()).isZero();
    }

    @Test
    void aNegativeSumIsRejected() {
        assertThatThrownBy(() -> new RatingTally(-1L, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aNegativeCountIsRejected() {
        assertThatThrownBy(() -> new RatingTally(0L, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
