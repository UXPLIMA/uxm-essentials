package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.bukkit.entity.Display;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.holograms.domain.Appearance;
import com.uxplima.uxmessentials.holograms.domain.Billboard;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmlib.hologram.HologramSpec;
import org.junit.jupiter.api.Test;

/**
 * Pins how {@link HologramRenderer#builderFor} turns a domain hologram into the uxmLib builder, asserting on
 * the pure {@link HologramSpec} (no world, no entity): a {@code %papi%} token is resolved through the injected
 * bridge before MiniMessage parses the line, and the domain {@link Appearance} maps onto the lib's appearance.
 */
class HologramRendererBuilderTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void resolvesAPlaceholderThroughTheBridgeBeforeMiniMessage() {
        Hologram hologram = hologram(Appearance.defaults(), "Online: %server_online%");
        UnaryOperator<String> fakeBridge = source -> source.replace("%server_online%", "42");

        HologramSpec spec =
                HologramRenderer.builderFor(hologram, fakeBridge, MINI_MESSAGE).spec();

        assertThat(PLAIN.serialize(spec.asText())).isEqualTo("Online: 42");
    }

    @Test
    void aLineWithNoPlaceholderIsLeftUntouchedByAnIdentityBridge() {
        Hologram hologram = hologram(Appearance.defaults(), "Welcome");

        HologramSpec spec = HologramRenderer.builderFor(hologram, UnaryOperator.identity(), MINI_MESSAGE)
                .spec();

        assertThat(PLAIN.serialize(spec.asText())).isEqualTo("Welcome");
    }

    @Test
    void mapsTheDomainAppearanceOntoTheBuilder() {
        Appearance styled = Appearance.defaults()
                .withBillboard(Billboard.FIXED)
                .withTextShadow(true)
                .withScale(2.0f)
                .withLineWidth(120)
                .withViewRange(3.0f)
                .withBackgroundArgb(0x80112233)
                .withBrightness(15, 7);
        Hologram hologram = hologram(styled, "styled");

        HologramSpec spec = HologramRenderer.builderFor(hologram, UnaryOperator.identity(), MINI_MESSAGE)
                .spec();

        com.uxplima.uxmlib.hologram.Appearance applied = spec.appearance();
        assertThat(applied.billboard()).isEqualTo(Display.Billboard.FIXED);
        assertThat(applied.textShadow()).isTrue();
        assertThat(applied.lineWidth()).isEqualTo(120);
        assertThat(applied.viewRange()).isEqualTo(3.0f);
        assertThat(applied.background()).isNotNull();
        assertThat(applied.brightness()).isNotNull();
    }

    @Test
    void mapsAFiniteVisibilityDistanceOntoTheLibViewRange() {
        // 128 blocks over the vanilla 64-block tracking range is a view-range multiplier of 2.0.
        Hologram hologram = hologram(Appearance.defaults(), "gated")
                .withVisibility(Visibility.everyone().withDistance(128));

        HologramSpec spec = HologramRenderer.builderFor(hologram, UnaryOperator.identity(), MINI_MESSAGE)
                .spec();

        assertThat(spec.appearance().viewRange()).isEqualTo(2.0f);
    }

    @Test
    void anUnlimitedDistanceLeavesTheAppearanceViewRangeUntouched() {
        Hologram hologram =
                hologram(Appearance.defaults().withViewRange(3.0f), "open").withVisibility(Visibility.everyone());

        HologramSpec spec = HologramRenderer.builderFor(hologram, UnaryOperator.identity(), MINI_MESSAGE)
                .spec();

        assertThat(spec.appearance().viewRange()).isEqualTo(3.0f);
    }

    private static Hologram hologram(Appearance appearance, String line) {
        return Hologram.create(
                        HologramName.of("h"),
                        Position.of(WORLD, 0, 64, 0),
                        List.of(new HologramLine(line)),
                        Instant.EPOCH)
                .withAppearance(appearance);
    }
}
