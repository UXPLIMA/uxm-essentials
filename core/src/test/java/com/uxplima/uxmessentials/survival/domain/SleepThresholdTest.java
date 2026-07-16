package com.uxplima.uxmessentials.survival.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * Pins the pure one-player-sleep threshold: a percentage rounds up to a whole player, a fixed count is honoured
 * verbatim, and the fixed count takes precedence over the percentage whenever it is positive.
 */
class SleepThresholdTest {

    @Test
    void percentageRoundsUpToAWholeSleepingPlayer() {
        // 50% of three eligible players rounds up to two: one sleeper is short, two is enough.
        SleepThreshold half = new SleepThreshold(0, 50);

        assertThat(half.requiredSleepers(3)).isEqualTo(2);
        assertThat(half.isMet(1, 3)).isFalse();
        assertThat(half.isMet(2, 3)).isTrue();
    }

    @Test
    void percentageNeedsAtLeastOneSleeperEvenWhenItRoundsToZero() {
        SleepThreshold tiny = new SleepThreshold(0, 10); // 10% of 2 = 0.2, rounds up to 1

        assertThat(tiny.requiredSleepers(2)).isEqualTo(1);
        assertThat(tiny.isMet(1, 2)).isTrue();
    }

    @Test
    void aFixedCountIsHonouredVerbatim() {
        SleepThreshold two = new SleepThreshold(2, 0);

        assertThat(two.requiredSleepers(5)).isEqualTo(2);
        assertThat(two.isMet(1, 5)).isFalse();
        assertThat(two.isMet(2, 5)).isTrue();
    }

    @Test
    void theFixedCountTakesPrecedenceOverThePercentageWhenPositive() {
        // Count of one plus a 100% percentage: the count wins, so a single sleeper among ten skips the night.
        SleepThreshold onePlayer = new SleepThreshold(1, 100);

        assertThat(onePlayer.requiredSleepers(10)).isEqualTo(1);
        assertThat(onePlayer.isMet(1, 10)).isTrue();
    }

    @Test
    void aCountHigherThanTheOnlinePopulationDoesNotSkip() {
        // Three required but only two eligible and sleeping: honoured literally, so the night holds.
        SleepThreshold three = new SleepThreshold(3, 0);

        assertThat(three.isMet(2, 2)).isFalse();
    }

    @Test
    void nobodySleepingOrNobodyEligibleIsNeverMet() {
        SleepThreshold onePlayer = new SleepThreshold(1, 0);

        assertThat(onePlayer.isMet(0, 5)).isFalse();
        assertThat(onePlayer.isMet(1, 0)).isFalse();
    }

    @Test
    void rejectsNegativeCounts() {
        SleepThreshold onePlayer = new SleepThreshold(1, 0);

        assertThatIllegalArgumentException().isThrownBy(() -> onePlayer.isMet(-1, 5));
        assertThatIllegalArgumentException().isThrownBy(() -> onePlayer.isMet(1, -1));
    }
}
