package com.uxplima.uxmessentials.holograms.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TransformTest {

    @Test
    void defaultIsNoOffsetAndUnitScale() {
        Transform t = Transform.DEFAULT;
        assertThat(t.translationX()).isZero();
        assertThat(t.translationY()).isZero();
        assertThat(t.translationZ()).isZero();
        assertThat(t.scaleX()).isEqualTo(1.0f);
        assertThat(t.isUniformScale()).isTrue();
        assertThat(t.hasTranslation()).isFalse();
    }

    @Test
    void uniformScaleSetsEveryAxis() {
        Transform t = Transform.ofUniformScale(2.0f);
        assertThat(t.scaleX()).isEqualTo(2.0f);
        assertThat(t.scaleY()).isEqualTo(2.0f);
        assertThat(t.scaleZ()).isEqualTo(2.0f);
        assertThat(t.isUniformScale()).isTrue();
    }

    @Test
    void perAxisScaleIsNotUniform() {
        Transform t = Transform.DEFAULT.withScale(2.0f, 3.0f, 4.0f);
        assertThat(t.scaleX()).isEqualTo(2.0f);
        assertThat(t.scaleY()).isEqualTo(3.0f);
        assertThat(t.scaleZ()).isEqualTo(4.0f);
        assertThat(t.isUniformScale()).isFalse();
    }

    @Test
    void translationOffsetsAndReportsItself() {
        Transform t = Transform.DEFAULT.withTranslation(1.0f, -2.0f, 0.25f);
        assertThat(t.translationX()).isEqualTo(1.0f);
        assertThat(t.translationY()).isEqualTo(-2.0f);
        assertThat(t.translationZ()).isEqualTo(0.25f);
        assertThat(t.hasTranslation()).isTrue();
        // The translation change keeps the scale at its previous value.
        assertThat(t.scaleX()).isEqualTo(1.0f);
    }

    @Test
    void rejectsAnOutOfRangeScale() {
        assertThatThrownBy(() -> Transform.DEFAULT.withScale(0.0f, 1.0f, 1.0f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANonFiniteTranslation() {
        assertThatThrownBy(() -> Transform.DEFAULT.withTranslation(Float.NaN, 0f, 0f))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
