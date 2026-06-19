package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * Pins the pure idle-unload decision: only an empty world idle at-or-past a positive threshold unloads,
 * and a non-positive threshold disables unloading entirely.
 */
class AutoUnloadPolicyTest {

    @Test
    void doesNotUnloadWhilePlayersRemain() {
        assertThat(AutoUnloadPolicy.shouldUnload(1, Duration.ofMinutes(99), Duration.ofMinutes(30)))
                .isFalse();
    }

    @Test
    void doesNotUnloadBeforeTheThreshold() {
        assertThat(AutoUnloadPolicy.shouldUnload(0, Duration.ofMinutes(10), Duration.ofMinutes(30)))
                .isFalse();
    }

    @Test
    void unloadsWhenIdleExactlyAtTheThreshold() {
        assertThat(AutoUnloadPolicy.shouldUnload(0, Duration.ofMinutes(30), Duration.ofMinutes(30)))
                .isTrue();
    }

    @Test
    void unloadsWhenIdlePastTheThreshold() {
        assertThat(AutoUnloadPolicy.shouldUnload(0, Duration.ofMinutes(31), Duration.ofMinutes(30)))
                .isTrue();
    }

    @Test
    void aZeroThresholdDisablesUnloading() {
        assertThat(AutoUnloadPolicy.shouldUnload(0, Duration.ofMinutes(99), Duration.ZERO))
                .isFalse();
    }

    @Test
    @SuppressWarnings("NullAway") // verifies the method rejects a literal null idle duration
    void rejectsNullIdleFor() {
        assertThatThrownBy(() -> AutoUnloadPolicy.shouldUnload(0, null, Duration.ofMinutes(30)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // verifies the method rejects a literal null threshold
    void rejectsNullThreshold() {
        assertThatThrownBy(() -> AutoUnloadPolicy.shouldUnload(0, Duration.ofMinutes(30), null))
                .isInstanceOf(NullPointerException.class);
    }
}
