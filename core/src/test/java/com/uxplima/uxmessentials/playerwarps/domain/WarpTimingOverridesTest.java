package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class WarpTimingOverridesTest {

    @Test
    void noneLeavesBothOverridesAbsent() {
        WarpTimingOverrides timing = WarpTimingOverrides.none();

        assertThat(timing.warmupSeconds()).isEmpty();
        assertThat(timing.cooldownSeconds()).isEmpty();
    }

    @Test
    void acceptsPresentNonNegativeDurations() {
        WarpTimingOverrides timing = new WarpTimingOverrides(Optional.of(2.5), Optional.of(0.0));

        assertThat(timing.warmupSeconds()).contains(2.5);
        assertThat(timing.cooldownSeconds()).contains(0.0);
    }

    @Test
    void rejectsANegativeWarmup() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WarpTimingOverrides(Optional.of(-1.0), Optional.empty()));
    }

    @Test
    void rejectsANegativeCooldown() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WarpTimingOverrides(Optional.empty(), Optional.of(-0.5)));
    }

    @Test
    @SuppressWarnings("NullAway") // verifies the compact constructor rejects a literal null Optional
    void rejectsANullOptional() {
        assertThatNullPointerException().isThrownBy(() -> new WarpTimingOverrides(null, Optional.empty()));
    }
}
