package com.uxplima.uxmessentials.holograms.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AppearanceTest {

    @Test
    void defaultsAreTheVanillaStyling() {
        Appearance defaults = Appearance.defaults();

        assertThat(defaults.billboard()).isEqualTo(Billboard.CENTER);
        assertThat(defaults.textShadow()).isFalse();
        assertThat(defaults.scale()).isEqualTo(1.0f);
        assertThat(defaults.lineWidth()).isEqualTo(200);
        assertThat(defaults.viewRange()).isEqualTo(1.0f);
        assertThat(defaults.hasBackground()).isFalse();
        assertThat(defaults.hasBrightness()).isFalse();
        assertThat(defaults.backgroundArgb()).isEqualTo(Appearance.DEFAULT_BACKGROUND);
        assertThat(defaults.brightnessBlock()).isEqualTo(Appearance.DEFAULT_BRIGHTNESS);
        assertThat(defaults.seeThrough()).isFalse();
        assertThat(defaults.alignment()).isEqualTo(TextAlignment.CENTER);
        assertThat(defaults.hasShadowRadius()).isFalse();
        assertThat(defaults.hasShadowStrength()).isFalse();
        assertThat(defaults.hasGlow()).isFalse();
        assertThat(defaults.hasTextOpacity()).isFalse();
        assertThat(defaults.glowArgb()).isEqualTo(Appearance.DEFAULT_GLOW);
        assertThat(defaults.textOpacity()).isEqualTo(Appearance.DEFAULT_OPACITY);
        assertThat(defaults.transform()).isEqualTo(Transform.DEFAULT);
        assertThat(defaults.transform().isUniformScale()).isTrue();
    }

    @Test
    void withTransitionsKeepEveryOtherField() {
        Appearance styled = Appearance.defaults()
                .withBillboard(Billboard.FIXED)
                .withBackgroundArgb(0x80112233)
                .withTextShadow(true)
                .withBrightness(15, 7)
                .withScale(2.5f)
                .withLineWidth(120)
                .withViewRange(3.0f);

        assertThat(styled.billboard()).isEqualTo(Billboard.FIXED);
        assertThat(styled.backgroundArgb()).isEqualTo(0x80112233);
        assertThat(styled.hasBackground()).isTrue();
        assertThat(styled.textShadow()).isTrue();
        assertThat(styled.brightnessBlock()).isEqualTo(15);
        assertThat(styled.brightnessSky()).isEqualTo(7);
        assertThat(styled.hasBrightness()).isTrue();
        assertThat(styled.scale()).isEqualTo(2.5f);
        assertThat(styled.lineWidth()).isEqualTo(120);
        assertThat(styled.viewRange()).isEqualTo(3.0f);
    }

    @Test
    void glowAndOpacityRoundTripAndClearBackToTheDefault() {
        Appearance styled = Appearance.defaults().withGlowArgb(0x80ff8800).withTextOpacity(200);

        assertThat(styled.hasGlow()).isTrue();
        assertThat(styled.glowArgb()).isEqualTo(0x80ff8800);
        assertThat(styled.hasTextOpacity()).isTrue();
        assertThat(styled.textOpacity()).isEqualTo(200);
        // Other fields untouched by the glow/opacity transitions.
        assertThat(styled.billboard()).isEqualTo(Appearance.defaults().billboard());

        Appearance cleared = styled.withGlowArgb(Appearance.DEFAULT_GLOW).withTextOpacity(Appearance.DEFAULT_OPACITY);
        assertThat(cleared.hasGlow()).isFalse();
        assertThat(cleared.hasTextOpacity()).isFalse();
    }

    @Test
    void textOpacityOutOfRangeIsRejected() {
        assertThatThrownBy(() -> Appearance.defaults().withTextOpacity(256))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Appearance.defaults().withTextOpacity(-5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Appearance.clampOpacity(999)).isEqualTo(255);
        assertThat(Appearance.clampOpacity(-3)).isEqualTo(0);
    }

    @Test
    void newDisplayPropertiesRoundTripAndKeepOtherFields() {
        Appearance styled = Appearance.defaults()
                .withSeeThrough(true)
                .withAlignment(TextAlignment.LEFT)
                .withShadowRadius(1.5f)
                .withShadowStrength(0.8f)
                .withScale(2f, 3f, 4f)
                .withTranslation(0.5f, 1f, -0.5f);

        assertThat(styled.seeThrough()).isTrue();
        assertThat(styled.alignment()).isEqualTo(TextAlignment.LEFT);
        assertThat(styled.shadowRadius()).isEqualTo(1.5f);
        assertThat(styled.shadowStrength()).isEqualTo(0.8f);
        assertThat(styled.hasShadowRadius()).isTrue();
        assertThat(styled.hasShadowStrength()).isTrue();
        assertThat(styled.transform().scaleX()).isEqualTo(2f);
        assertThat(styled.transform().scaleY()).isEqualTo(3f);
        assertThat(styled.transform().scaleZ()).isEqualTo(4f);
        assertThat(styled.transform().isUniformScale()).isFalse();
        assertThat(styled.transform().translationX()).isEqualTo(0.5f);
        assertThat(styled.transform().translationY()).isEqualTo(1f);
        assertThat(styled.transform().translationZ()).isEqualTo(-0.5f);
        // The legacy uniform scale() accessor reports the X axis, preserving the SCALE column semantics.
        assertThat(styled.scale()).isEqualTo(2f);
    }

    @Test
    void uniformScaleKeepsEveryAxisEqualAndTranslation() {
        Appearance styled = Appearance.defaults().withTranslation(0f, 2f, 0f).withScale(1.5f);

        assertThat(styled.transform().scaleX()).isEqualTo(1.5f);
        assertThat(styled.transform().scaleY()).isEqualTo(1.5f);
        assertThat(styled.transform().scaleZ()).isEqualTo(1.5f);
        // A uniform scale change keeps an earlier translation.
        assertThat(styled.transform().translationY()).isEqualTo(2f);
    }

    @Test
    void rejectsAnOutOfRangeScale() {
        assertThatThrownBy(() -> Appearance.defaults().withScale(0.0f)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnOutOfRangePerAxisScale() {
        assertThatThrownBy(() -> Appearance.defaults().withScale(2f, 1000f, 1f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnOutOfRangeShadow() {
        assertThatThrownBy(() -> Appearance.defaults().withShadowRadius(1000f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alignmentParsesCaseInsensitivelyAndRejectsUnknown() {
        assertThat(TextAlignment.parse("left")).contains(TextAlignment.LEFT);
        assertThat(TextAlignment.parse("CENTER")).contains(TextAlignment.CENTER);
        assertThat(TextAlignment.parse("justified")).isEmpty();
    }

    @Test
    void rejectsAnOutOfRangeBrightnessChannel() {
        assertThatThrownBy(() -> Appearance.defaults().withBrightness(16, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clampingKeepsOperatorInputInRange() {
        assertThat(Appearance.clampScale(1000.0f)).isLessThanOrEqualTo(64.0f);
        assertThat(Appearance.clampScale(0.0f)).isGreaterThan(0.0f);
        assertThat(Appearance.clampLineWidth(0)).isGreaterThanOrEqualTo(1);
        assertThat(Appearance.clampViewRange(-5.0f)).isGreaterThanOrEqualTo(0.0f);
        assertThat(Appearance.clampShadow(1000.0f)).isLessThanOrEqualTo(100.0f);
        assertThat(Appearance.clampShadow(-5.0f)).isGreaterThanOrEqualTo(0.0f);
        assertThat(Appearance.clampTranslation(10_000.0f)).isLessThanOrEqualTo(256.0f);
        assertThat(Appearance.clampTranslation(-10_000.0f)).isGreaterThanOrEqualTo(-256.0f);
    }

    @Test
    void billboardParsesCaseInsensitivelyAndRejectsUnknown() {
        assertThat(Billboard.parse("horizontal")).contains(Billboard.HORIZONTAL);
        assertThat(Billboard.parse("CENTER")).contains(Billboard.CENTER);
        assertThat(Billboard.parse("diagonal")).isEmpty();
    }
}
