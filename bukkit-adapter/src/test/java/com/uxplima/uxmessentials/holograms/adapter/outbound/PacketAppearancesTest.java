package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import com.uxplima.uxmessentials.holograms.domain.Appearance;
import com.uxplima.uxmessentials.holograms.domain.Billboard;
import com.uxplima.uxmessentials.holograms.domain.TextAlignment;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmlib.packet.display.HologramAppearance;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * A stored hologram's look, carried onto the packet hologram property by property. A property the operator never
 * set must draw what the game draws by default, because that is what the display entity showed before: a missing
 * background is the game's grey panel, not a transparent one.
 */
class PacketAppearancesTest {

    /** The panel a text display draws when nobody sets a background. */
    private static final int VANILLA_BACKGROUND = 0x40000000;

    @Test
    void anAppearanceNobodyChangedDrawsTheGamesOwnLook() {
        HologramAppearance mapped = PacketAppearances.of(Appearance.defaults());

        assertThat(mapped.billboard()).isEqualTo(Display.Billboard.CENTER);
        assertThat(mapped.backgroundArgb()).isEqualTo(VANILLA_BACKGROUND);
        assertThat(mapped.textShadow()).isFalse();
        assertThat(mapped.seeThrough()).isFalse();
        assertThat(mapped.alignment()).isEqualTo(TextDisplay.TextAlignment.CENTER);
        assertThat(mapped.lineWidth()).isEqualTo(200);
        assertThat(mapped.viewRange()).isEqualTo(1.0f);
        assertThat(mapped.translation()).isEqualTo(new Vector3f(0f, 0f, 0f));
        assertThat(mapped.scale()).isEqualTo(new Vector3f(1f, 1f, 1f));
        assertThat(mapped.textOpacity()).isEqualTo(HologramAppearance.FULL_OPACITY);
        assertThat(mapped.hasGlow()).isFalse();
        assertThat(mapped.hasBrightness()).isFalse();
        assertThat(mapped.hasShadow()).isFalse();
    }

    @Test
    void everyPropertyTheOperatorSetIsCarried() {
        Appearance set = Appearance.defaults()
                .withBillboard(Billboard.FIXED)
                .withBackgroundArgb(0x80112233)
                .withTextShadow(true)
                .withSeeThrough(true)
                .withAlignment(TextAlignment.LEFT)
                .withLineWidth(120)
                .withViewRange(2.5f)
                .withTranslation(0.5f, 1.0f, -0.25f)
                .withScale(2f, 3f, 4f)
                .withTextOpacity(128)
                .withGlowArgb(0xFFFF0000)
                .withBrightness(12, 7)
                .withShadowRadius(1.5f)
                .withShadowStrength(0.75f);

        HologramAppearance mapped = PacketAppearances.of(set);

        assertThat(mapped.billboard()).isEqualTo(Display.Billboard.FIXED);
        assertThat(mapped.backgroundArgb()).isEqualTo(0x80112233);
        assertThat(mapped.textShadow()).isTrue();
        assertThat(mapped.seeThrough()).isTrue();
        assertThat(mapped.alignment()).isEqualTo(TextDisplay.TextAlignment.LEFT);
        assertThat(mapped.lineWidth()).isEqualTo(120);
        assertThat(mapped.viewRange()).isEqualTo(2.5f);
        assertThat(mapped.translation()).isEqualTo(new Vector3f(0.5f, 1.0f, -0.25f));
        assertThat(mapped.scale()).isEqualTo(new Vector3f(2f, 3f, 4f));
        assertThat(mapped.textOpacity()).isEqualTo(128);
        assertThat(mapped.glowArgb()).isEqualTo(0xFFFF0000);
        assertThat(mapped.brightnessBlock()).isEqualTo(12);
        assertThat(mapped.brightnessSky()).isEqualTo(7);
        assertThat(mapped.shadowRadius()).isEqualTo(1.5f);
        assertThat(mapped.shadowStrength()).isEqualTo(0.75f);
    }

    /** One light level set and the other left: the one left is dark, as the display entity drew it. */
    @Test
    void aBrightnessWithOneHalfSetLeavesTheOtherAtZero() {
        HologramAppearance mapped = PacketAppearances.of(Appearance.defaults().withBrightness(9, -1));

        assertThat(mapped.brightnessBlock()).isEqualTo(9);
        assertThat(mapped.brightnessSky()).isZero();
    }

    /** A shadow with only its radius set keeps the game's own strength. */
    @Test
    void aShadowWithOneHalfSetKeepsTheGamesOtherHalf() {
        HologramAppearance mapped = PacketAppearances.of(Appearance.defaults().withShadowRadius(2f));

        assertThat(mapped.shadowRadius()).isEqualTo(2f);
        assertThat(mapped.shadowStrength()).isEqualTo(HologramAppearance.VANILLA_SHADOW);
    }

    @Test
    void aDistanceLimitIsHowFarAViewerMayStand() {
        assertThat(PacketAppearances.viewDistance(Visibility.everyone().withDistance(24), Appearance.defaults()))
                .isEqualTo(24.0);
    }

    /** With no limit a viewer sees it as far as the display entity's own tracking range reached. */
    @Test
    void withoutALimitTheViewRangeDecidesHowFar() {
        assertThat(PacketAppearances.viewDistance(
                        Visibility.everyone(), Appearance.defaults().withViewRange(0.5f)))
                .isEqualTo(32.0);
    }
}
