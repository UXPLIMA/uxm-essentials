package com.uxplima.uxmessentials.nametags.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

class NametagAppearanceTest {

    @Test
    void defaultsAreCenterBillboardedUnitScaleAboveTheHead() {
        NametagAppearance defaults = NametagAppearance.defaults();

        assertThat(defaults.billboard()).isEqualTo("CENTER");
        assertThat(defaults.seeThrough()).isFalse();
        assertThat(defaults.backgroundColor()).isEmpty();
        assertThat(defaults.backgroundOpacity()).isEmpty();
        assertThat(defaults.textShadow()).isFalse();
        assertThat(defaults.glowColor()).isEmpty();
        assertThat(defaults.lineWidth()).isEmpty();
        assertThat(defaults.viewRange()).isEmpty();
        assertThat(defaults.scale()).isEqualTo(1.0);
        assertThat(defaults.yOffset()).isEqualTo(0.3);
    }

    @Test
    void carriesItsOptionalParts() {
        NametagAppearance appearance = new NametagAppearance(
                "FIXED",
                true,
                Optional.of("#101010"),
                OptionalInt.of(180),
                true,
                Optional.of("red"),
                OptionalInt.of(200),
                OptionalDouble.of(48.0),
                1.5,
                0.5);

        assertThat(appearance.billboard()).isEqualTo("FIXED");
        assertThat(appearance.seeThrough()).isTrue();
        assertThat(appearance.backgroundColor()).contains("#101010");
        assertThat(appearance.backgroundOpacity()).hasValue(180);
        assertThat(appearance.textShadow()).isTrue();
        assertThat(appearance.glowColor()).contains("red");
        assertThat(appearance.lineWidth()).hasValue(200);
        assertThat(appearance.viewRange()).hasValue(48.0);
        assertThat(appearance.scale()).isEqualTo(1.5);
        assertThat(appearance.yOffset()).isEqualTo(0.5);
    }

    @Test
    void rejectsNonPositiveScale() {
        assertThatThrownBy(() -> appearanceWithScale(0.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> appearanceWithScale(-1.0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBackgroundOpacityOutsideZeroToTwoFiftyFive() {
        assertThatThrownBy(() -> appearanceWithOpacity(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> appearanceWithOpacity(256)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsBackgroundOpacityAtTheBounds() {
        assertThat(appearanceWithOpacity(0).backgroundOpacity()).hasValue(0);
        assertThat(appearanceWithOpacity(255).backgroundOpacity()).hasValue(255);
    }

    @Test
    void rejectsANonFiniteYOffset() {
        assertThatThrownBy(() -> appearanceWithYOffset(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> appearanceWithYOffset(Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static NametagAppearance appearanceWithScale(double scale) {
        return new NametagAppearance(
                "CENTER",
                false,
                Optional.empty(),
                OptionalInt.empty(),
                false,
                Optional.empty(),
                OptionalInt.empty(),
                OptionalDouble.empty(),
                scale,
                0.3);
    }

    private static NametagAppearance appearanceWithOpacity(int opacity) {
        return new NametagAppearance(
                "CENTER",
                false,
                Optional.of("#000000"),
                OptionalInt.of(opacity),
                false,
                Optional.empty(),
                OptionalInt.empty(),
                OptionalDouble.empty(),
                1.0,
                0.3);
    }

    private static NametagAppearance appearanceWithYOffset(double yOffset) {
        return new NametagAppearance(
                "CENTER",
                false,
                Optional.empty(),
                OptionalInt.empty(),
                false,
                Optional.empty(),
                OptionalInt.empty(),
                OptionalDouble.empty(),
                1.0,
                yOffset);
    }
}
