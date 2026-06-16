package com.uxplima.uxmessentials.holograms.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RotationTest {

    @Test
    void noneIsBothZeroAndNotRotated() {
        assertThat(Rotation.NONE.yaw()).isZero();
        assertThat(Rotation.NONE.pitch()).isZero();
        assertThat(Rotation.NONE.isRotated()).isFalse();
    }

    @Test
    void keepsAnInRangeAngle() {
        Rotation r = Rotation.of(90f, 45f);

        assertThat(r.yaw()).isEqualTo(90f);
        assertThat(r.pitch()).isEqualTo(45f);
        assertThat(r.isRotated()).isTrue();
    }

    @Test
    void wrapsAnAngleAboveAFullTurn() {
        Rotation r = Rotation.of(450f, 360f);

        assertThat(r.yaw()).isEqualTo(90f);
        assertThat(r.pitch()).isZero();
    }

    @Test
    void wrapsANegativeAngleIntoRange() {
        Rotation r = Rotation.of(-90f, -10f);

        assertThat(r.yaw()).isEqualTo(270f);
        assertThat(r.pitch()).isEqualTo(350f);
    }

    @Test
    void rejectsANonFiniteAngle() {
        assertThatThrownBy(() -> Rotation.of(Float.NaN, 0f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Rotation.of(0f, Float.POSITIVE_INFINITY)).isInstanceOf(IllegalArgumentException.class);
    }
}
