package com.uxplima.uxmessentials.nametags.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.uxplima.uxmessentials.nametags.domain.NametagAppearance;
import com.uxplima.uxmlib.nametag.Alignment;
import com.uxplima.uxmlib.nametag.Appearance;
import com.uxplima.uxmlib.nametag.Billboard;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link NametagAppearanceMapper}: the billboard/background/scale/offset/line-width/view-range/line-of-sight
 * mapping onto the packet renderer's {@link Appearance}, the background-colour-plus-opacity fold into one ARGB int, and
 * the tolerant fallbacks for bad values. These are pure transforms over plain value types, so no MockBukkit server is
 * needed.
 */
class NametagAppearanceMapperTest {

    @Test
    void mapsEveryAuthoredFieldOntoThePacketAppearance() {
        NametagAppearance appearance = new NametagAppearance(
                "FIXED",
                true,
                Optional.of("#112233"),
                OptionalInt.empty(),
                true,
                Optional.of("#FF5555"),
                OptionalInt.of(200),
                OptionalDouble.of(2.5),
                1.5,
                0.4,
                true,
                OptionalInt.of(32));

        Appearance mapped = NametagAppearanceMapper.toAppearance(appearance);

        assertThat(mapped.billboard()).isEqualTo(Billboard.FIXED);
        assertThat(mapped.seeThrough()).isTrue();
        assertThat(mapped.textShadow()).isTrue();
        // No background opacity authored and a 6-digit colour: full opacity in the alpha channel.
        assertThat(mapped.backgroundArgb()).isEqualTo(0xFF112233);
        assertThat(mapped.lineWidth()).isEqualTo(200);
        assertThat(mapped.viewRange()).isEqualTo(2.5f);
        assertThat(mapped.alignment()).isEqualTo(Alignment.CENTER);
        assertThat(mapped.translation().y()).isEqualTo(0.4f);
        assertThat(mapped.scale().x()).isEqualTo(1.5f);
        assertThat(mapped.scale().y()).isEqualTo(1.5f);
        assertThat(mapped.scale().z()).isEqualTo(1.5f);
        assertThat(mapped.hideThroughBlocks()).isTrue();
        assertThat(mapped.obscuredOpacity()).isEqualTo(32);
    }

    @Test
    void foldsBackgroundOpacityIntoTheBackgroundColourAlpha() {
        NametagAppearance appearance = appearanceWith(Optional.of("#FFFFFF"), OptionalInt.of(64));

        Appearance mapped = NametagAppearanceMapper.toAppearance(appearance);

        // The opacity becomes the alpha channel of the background ARGB int.
        assertThat(mapped.backgroundArgb()).isEqualTo(0x40FFFFFF);
    }

    @Test
    void dropsBackgroundOpacityWithNoBackgroundColourToCarryIt() {
        NametagAppearance appearance = appearanceWith(Optional.empty(), OptionalInt.of(128));

        Appearance mapped = NametagAppearanceMapper.toAppearance(appearance);

        // No background colour authored: there is nothing to carry the opacity, so the transparent default (0) is kept.
        assertThat(mapped.backgroundArgb()).isEqualTo(Appearance.defaults().backgroundArgb());
    }

    @Test
    void parsesAnEightDigitArgbBackground() {
        NametagAppearance appearance = appearanceWith(Optional.of("#80112233"), OptionalInt.empty());

        Appearance mapped = NametagAppearanceMapper.toAppearance(appearance);

        assertThat(mapped.backgroundArgb()).isEqualTo(0x80112233);
    }

    @Test
    void anUnknownBillboardFallsBackToCenter() {
        assertThat(NametagAppearanceMapper.billboard("sideways")).isEqualTo(Billboard.CENTER);
        assertThat(NametagAppearanceMapper.billboard("vertical")).isEqualTo(Billboard.VERTICAL);
    }

    @Test
    void anUnparseableBackgroundIsSkippedAndDefaultsAreKept() {
        NametagAppearance bad = new NametagAppearance(
                "CENTER",
                false,
                Optional.of("not-a-colour"),
                OptionalInt.empty(),
                false,
                Optional.of("#ZZZZZZ"),
                OptionalInt.empty(),
                OptionalDouble.empty(),
                1.0,
                0.3,
                false,
                OptionalInt.empty());

        Appearance mapped = NametagAppearanceMapper.toAppearance(bad);
        Appearance defaults = Appearance.defaults();

        assertThat(mapped.backgroundArgb()).isEqualTo(defaults.backgroundArgb());
        // The lib defaults still apply where the operator authored nothing valid.
        assertThat(mapped.lineWidth()).isEqualTo(defaults.lineWidth());
        assertThat(mapped.viewRange()).isEqualTo(defaults.viewRange());
        assertThat(mapped.scale().x()).isEqualTo(1.0f);
        // An absent obscured-opacity with hide-through-blocks off still maps the renderer's quarter-opacity default.
        assertThat(mapped.obscuredOpacity()).isEqualTo(64);
    }

    private static NametagAppearance appearanceWith(Optional<String> background, OptionalInt opacity) {
        return new NametagAppearance(
                "CENTER",
                false,
                background,
                opacity,
                false,
                Optional.empty(),
                OptionalInt.empty(),
                OptionalDouble.empty(),
                1.0,
                0.3,
                false,
                OptionalInt.empty());
    }
}
