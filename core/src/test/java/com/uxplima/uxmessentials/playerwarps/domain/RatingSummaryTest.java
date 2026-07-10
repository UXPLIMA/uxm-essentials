package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RatingSummaryTest {

    @Test
    void emptyHasNoVotesAndZeroScores() {
        RatingSummary summary = RatingSummary.empty();
        assertThat(summary.sum()).isZero();
        assertThat(summary.count()).isZero();
        assertThat(summary.average()).isZero();
        assertThat(summary.score()).isZero();
    }

    @Test
    void ofStoresTheTotalsAndPrecomputedScoreVerbatim() {
        RatingSummary summary = RatingSummary.of(240L, 50, 4.8, 4.61);
        assertThat(summary.sum()).isEqualTo(240L);
        assertThat(summary.count()).isEqualTo(50);
        assertThat(summary.average()).isEqualTo(4.8);
        assertThat(summary.score()).isEqualTo(4.61);
    }

    @Test
    void rejectsNegativeSum() {
        assertThatThrownBy(() -> RatingSummary.of(-1L, 0, 0.0, 0.0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeCount() {
        assertThatThrownBy(() -> RatingSummary.of(0L, -1, 0.0, 0.0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonFiniteAverage() {
        assertThatThrownBy(() -> RatingSummary.of(0L, 0, Double.NaN, 0.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RatingSummary.of(0L, 0, Double.POSITIVE_INFINITY, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonFiniteScore() {
        assertThatThrownBy(() -> RatingSummary.of(0L, 0, 0.0, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RatingSummary.of(0L, 0, 0.0, Double.NEGATIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
