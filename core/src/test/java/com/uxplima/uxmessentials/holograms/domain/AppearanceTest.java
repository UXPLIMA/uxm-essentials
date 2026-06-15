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
    void rejectsAnOutOfRangeScale() {
        assertThatThrownBy(() -> Appearance.defaults().withScale(0.0f)).isInstanceOf(IllegalArgumentException.class);
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
    }

    @Test
    void billboardParsesCaseInsensitivelyAndRejectsUnknown() {
        assertThat(Billboard.parse("horizontal")).contains(Billboard.HORIZONTAL);
        assertThat(Billboard.parse("CENTER")).contains(Billboard.CENTER);
        assertThat(Billboard.parse("diagonal")).isEmpty();
    }
}
