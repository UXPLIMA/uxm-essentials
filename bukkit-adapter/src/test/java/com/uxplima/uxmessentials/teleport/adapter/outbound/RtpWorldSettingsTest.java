package com.uxplima.uxmessentials.teleport.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The {@code /settpr} runtime swap on {@link RtpWorldSettings#withRadii(double, double)}: it resets the search
 * band while leaving the queue sizing and attempt budget untouched, and it keeps the record's
 * {@code maxRadius >= minRadius} invariant so a back-to-front band is rejected rather than silently stored.
 */
class RtpWorldSettingsTest {

    private static final RtpWorldSettings BASE = new RtpWorldSettings(100, 5000, 10, 5, 32);

    @Test
    void withRadiiSwapsTheBandAndKeepsTheQueueTuning() {
        RtpWorldSettings updated = BASE.withRadii(250, 8000);

        assertThat(updated.minRadius()).isEqualTo(250);
        assertThat(updated.maxRadius()).isEqualTo(8000);
        assertThat(updated.targetSize()).isEqualTo(BASE.targetSize());
        assertThat(updated.lowWaterMark()).isEqualTo(BASE.lowWaterMark());
        assertThat(updated.attemptBudget()).isEqualTo(BASE.attemptBudget());
    }

    @Test
    void withRadiiRejectsAMaxBelowTheMin() {
        assertThatThrownBy(() -> BASE.withRadii(2000, 1000)).isInstanceOf(IllegalArgumentException.class);
    }
}
