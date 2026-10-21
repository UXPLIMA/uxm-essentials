package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import com.uxplima.uxmessentials.holograms.domain.Appearance;
import com.uxplima.uxmessentials.holograms.domain.Billboard;
import com.uxplima.uxmessentials.holograms.domain.TextAlignment;
import com.uxplima.uxmessentials.holograms.domain.Transform;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmlib.packet.display.HologramAppearance;
import org.joml.Vector3f;
import org.jspecify.annotations.NullMarked;

/**
 * A stored hologram's {@link Appearance}, as the packet hologram draws it. A property the operator never set draws
 * what the game draws for a display entity nobody configured, which is what a hologram showed before it was drawn
 * with packets: the grey panel behind text, full opacity, the world's own light and the game's own shadow.
 */
@NullMarked
final class PacketAppearances {

    /** The panel a text display draws when nobody sets a background: black at a quarter opacity. */
    static final int VANILLA_BACKGROUND = 0x40000000;

    /** The vanilla display entity tracking range in blocks at view-range multiplier 1.0. */
    static final double VANILLA_VIEW_BLOCKS = 64.0;

    private PacketAppearances() {}

    static HologramAppearance of(Appearance appearance) {
        Objects.requireNonNull(appearance, "appearance");
        Transform transform = appearance.transform();
        return new HologramAppearance(
                billboard(appearance.billboard()),
                Color.fromARGB(appearance.hasBackground() ? appearance.backgroundArgb() : VANILLA_BACKGROUND),
                appearance.textShadow(),
                appearance.seeThrough(),
                alignment(appearance.alignment()),
                appearance.lineWidth(),
                appearance.viewRange(),
                new Vector3f(transform.translationX(), transform.translationY(), transform.translationZ()),
                new Vector3f(transform.scaleX(), transform.scaleY(), transform.scaleZ()),
                appearance.hasTextOpacity() ? appearance.textOpacity() : HologramAppearance.FULL_OPACITY,
                appearance.hasGlow() ? appearance.glowArgb() : HologramAppearance.NO_GLOW,
                brightness(appearance, appearance.brightnessBlock()),
                brightness(appearance, appearance.brightnessSky()),
                appearance.hasShadowRadius() ? appearance.shadowRadius() : HologramAppearance.VANILLA_SHADOW,
                appearance.hasShadowStrength() ? appearance.shadowStrength() : HologramAppearance.VANILLA_SHADOW,
                HologramAppearance.DEFAULT_LINE_GAP);
    }

    /**
     * How far a viewer may stand and still be sent the hologram: the visibility's distance when it sets one,
     * otherwise as far as a display entity's tracking range reached at the appearance's view-range multiplier.
     */
    static double viewDistance(Visibility visibility, Appearance appearance) {
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(appearance, "appearance");
        if (visibility.hasDistanceLimit()) {
            return visibility.distance();
        }
        return Math.max(1.0, appearance.viewRange() * VANILLA_VIEW_BLOCKS);
    }

    private static Display.Billboard billboard(Billboard billboard) {
        return switch (billboard) {
            case CENTER -> Display.Billboard.CENTER;
            case FIXED -> Display.Billboard.FIXED;
            case VERTICAL -> Display.Billboard.VERTICAL;
            case HORIZONTAL -> Display.Billboard.HORIZONTAL;
        };
    }

    private static TextDisplay.TextAlignment alignment(TextAlignment alignment) {
        return switch (alignment) {
            case CENTER -> TextDisplay.TextAlignment.CENTER;
            case LEFT -> TextDisplay.TextAlignment.LEFT;
            case RIGHT -> TextDisplay.TextAlignment.RIGHT;
        };
    }

    /**
     * One light level: the world's own light when neither is set, and when only the other one is set, zero, which
     * is what the display entity drew for the half nobody set.
     */
    private static int brightness(Appearance appearance, int level) {
        if (!appearance.hasBrightness()) {
            return HologramAppearance.WORLD_BRIGHTNESS;
        }
        return Appearance.isDefaultBrightness(level) ? 0 : level;
    }
}
