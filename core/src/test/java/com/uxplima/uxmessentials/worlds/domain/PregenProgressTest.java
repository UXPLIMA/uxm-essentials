package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class PregenProgressTest {

    @Test
    void fractionReportsRatioClampedToOne() {
        assertThat(progress(0, 10).fraction()).isCloseTo(0.0, within(1e-9));
        assertThat(progress(5, 10).fraction()).isCloseTo(0.5, within(1e-9));
        assertThat(progress(10, 10).fraction()).isCloseTo(1.0, within(1e-9));
        assertThat(progress(12, 10).fraction()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void fractionOfAnEmptyJobIsComplete() {
        assertThat(progress(0, 0).fraction()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void etaIsEmptyWhenNothingDone() {
        assertThat(progress(0, 10).eta()).isEmpty();
    }

    @Test
    void etaIsEmptyWhenAlreadyComplete() {
        assertThat(progress(10, 10).eta()).isEmpty();
        assertThat(progress(12, 10).eta()).isEmpty();
    }

    @Test
    void etaExtrapolatesFromTheCurrentRate() {
        PregenProgress progress = new PregenProgress(2, 10, Duration.ofSeconds(4));
        assertThat(progress.eta()).isEqualTo(Optional.of(Duration.ofSeconds(16)));
    }

    @Test
    void rejectsNegativeCounters() {
        assertThatThrownBy(() -> new PregenProgress(-1, 10, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PregenProgress(0, -1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately feeds null to verify the constructor rejects it at runtime
    void rejectsNullElapsed() {
        assertThatThrownBy(() -> new PregenProgress(1, 10, null)).isInstanceOf(NullPointerException.class);
    }

    private static PregenProgress progress(long done, long total) {
        return new PregenProgress(done, total, Duration.ofSeconds(1));
    }
}
