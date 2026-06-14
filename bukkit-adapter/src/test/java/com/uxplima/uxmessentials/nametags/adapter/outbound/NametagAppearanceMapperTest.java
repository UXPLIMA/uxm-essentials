package com.uxplima.uxmessentials.nametags.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.bukkit.Color;
import org.bukkit.entity.Display;

import com.uxplima.uxmessentials.nametags.domain.NametagAppearance;
import com.uxplima.uxmlib.hologram.Appearance;
import com.uxplima.uxmlib.hologram.Transform;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link NametagAppearanceMapper}: the billboard/colour/opacity/scale/line-width/view-range mapping onto
 * uxmLib's {@link Appearance}, the background-opacity-into-alpha fold, and the tolerant fallbacks for bad values. These
 * are pure transforms over plain Bukkit value types ({@link Color}, {@link Display.Billboard}), so no MockBukkit server
 * is needed.
 */
class NametagAppearanceMapperTest {

    @Test
    void mapsEveryAuthoredFieldOntoTheUxmlibAppearance() {
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
                0.4);

        Appearance mapped = NametagAppearanceMapper.toAppearance(appearance);

        assertThat(mapped.billboard()).isEqualTo(Display.Billboard.FIXED);
        assertThat(mapped.seeThrough()).isTrue();
        assertThat(mapped.textShadow()).isTrue();
        assertThat(mapped.glow()).isEqualTo(Color.fromARGB(255, 0xFF, 0x55, 0x55));
        assertThat(mapped.background()).isEqualTo(Color.fromARGB(255, 0x11, 0x22, 0x33));
        assertThat(mapped.lineWidth()).isEqualTo(200);
        assertThat(mapped.viewRange()).isEqualTo(2.5f);
        assertThat(mapped.transform()).isEqualTo(Transform.scale(1.5f));
    }

    @Test
    void foldsBackgroundOpacityIntoTheBackgroundColourAlpha() {
        NametagAppearance appearance = appearanceWith(Optional.of("#FFFFFF"), OptionalInt.of(64));

        Appearance mapped = NametagAppearanceMapper.toAppearance(appearance);

        // The opacity becomes the alpha channel of the background colour (uxmLib has no separate opacity field).
        assertThat(mapped.background()).isEqualTo(Color.fromARGB(64, 0xFF, 0xFF, 0xFF));
    }

    @Test
    void dropsBackgroundOpacityWithNoBackgroundColourToCarryIt() {
        NametagAppearance appearance = appearanceWith(Optional.empty(), OptionalInt.of(128));

        Appearance mapped = NametagAppearanceMapper.toAppearance(appearance);

        // No background colour authored: there is nothing to carry the opacity, so the platform default is kept.
        assertThat(mapped.background()).isNull();
    }

    @Test
    void parsesAnEightDigitArgbBackground() {
        NametagAppearance appearance = appearanceWith(Optional.of("#80112233"), OptionalInt.empty());

        Appearance mapped = NametagAppearanceMapper.toAppearance(appearance);

        assertThat(mapped.background()).isEqualTo(Color.fromARGB(0x80, 0x11, 0x22, 0x33));
    }

    @Test
    void anUnknownBillboardFallsBackToCenter() {
        assertThat(NametagAppearanceMapper.billboard("sideways")).isEqualTo(Display.Billboard.CENTER);
        assertThat(NametagAppearanceMapper.billboard("vertical")).isEqualTo(Display.Billboard.VERTICAL);
    }

    @Test
    void anUnparseableColourIsSkippedRatherThanSubstituted() {
        NametagAppearance badGlow = new NametagAppearance(
                "CENTER",
                false,
                Optional.of("not-a-colour"),
                OptionalInt.empty(),
                false,
                Optional.of("#ZZZZZZ"),
                OptionalInt.empty(),
                OptionalDouble.empty(),
                1.0,
                0.3);

        Appearance mapped = NametagAppearanceMapper.toAppearance(badGlow);

        assertThat(mapped.background()).isNull();
        assertThat(mapped.glow()).isNull();
        // The defaults still apply: scale 1.0 and no line-width/view-range override.
        assertThat(mapped.lineWidth()).isNull();
        assertThat(mapped.viewRange()).isNull();
        assertThat(mapped.transform()).isEqualTo(Transform.scale(1.0f));
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
                0.3);
    }
}
