package com.uxplima.uxmessentials.playerstate.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class PlaytimeSummaryTest {

    @Test
    void aCorrectionMovesOnlyTheAllTimeTotals() {
        PlaytimeSummary summary =
                PlaytimeSummary.ofSeconds(60, 0, 60, 0, 60, 0, 60, 0).withAdjustment(600);

        assertThat(summary.todayActive()).isEqualTo(Duration.ofSeconds(60));
        assertThat(summary.totalActive()).isEqualTo(Duration.ofMinutes(11));
    }

    @Test
    void aNegativeCorrectionTakesFromAfkTimeOnceActiveTimeIsGone() {
        PlaytimeSummary summary =
                PlaytimeSummary.ofSeconds(0, 0, 0, 0, 0, 0, 100, 300).withAdjustment(-250);

        assertThat(summary.totalActive()).isZero();
        assertThat(summary.totalAfk()).isEqualTo(Duration.ofSeconds(150));
        assertThat(summary.totalCombined()).isEqualTo(Duration.ofSeconds(150));
    }

    @Test
    void noTotalGoesBelowZero() {
        PlaytimeSummary summary =
                PlaytimeSummary.ofSeconds(0, 0, 0, 0, 0, 0, 100, 100).withAdjustment(-1_000);

        assertThat(summary.totalCombined()).isZero();
    }
}
