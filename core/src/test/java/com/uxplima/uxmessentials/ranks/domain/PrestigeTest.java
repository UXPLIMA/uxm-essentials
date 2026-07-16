package com.uxplima.uxmessentials.ranks.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * Pure coverage of the {@link Prestige} level rules the prestige use case leans on: {@link Prestige#increment()}
 * only ever goes up, {@link Prestige#belowCap(int)} answers the level-cap eligibility (with {@code 0} meaning no
 * cap), and {@link Prestige#rewardMultiplier(double)} scales the reward linearly with level.
 */
class PrestigeTest {

    @Test
    void incrementGoesUpByOne() {
        assertThat(Prestige.INITIAL.increment()).isEqualTo(new Prestige(1));
        assertThat(new Prestige(4).increment()).isEqualTo(new Prestige(5));
    }

    @Test
    void belowCapIsUnlimitedWhenTheCapIsZeroOrLess() {
        assertThat(new Prestige(1000).belowCap(0)).isTrue();
        assertThat(new Prestige(1000).belowCap(-1)).isTrue();
    }

    @Test
    void belowCapIsTrueOnlyWhileTheLevelIsStrictlyBelowThePositiveCap() {
        assertThat(new Prestige(9).belowCap(10)).isTrue();
        assertThat(new Prestige(10).belowCap(10)).isFalse();
        assertThat(new Prestige(11).belowCap(10)).isFalse();
    }

    @Test
    void rewardMultiplierScalesLinearlyWithLevel() {
        assertThat(Prestige.INITIAL.rewardMultiplier(1.5)).isEqualTo(1.0);
        assertThat(new Prestige(1).rewardMultiplier(1.5)).isEqualTo(1.5);
        assertThat(new Prestige(2).rewardMultiplier(1.5)).isEqualTo(2.0);
        assertThat(new Prestige(3).rewardMultiplier(1.5)).isCloseTo(2.5, within(1e-9));
    }

    @Test
    void aConfiguredMultiplierOfOneGrantsNoBonusAtAnyLevel() {
        assertThat(new Prestige(7).rewardMultiplier(1.0)).isEqualTo(1.0);
    }

    @Test
    void rejectsANegativeLevel() {
        assertThatThrownBy(() -> new Prestige(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
