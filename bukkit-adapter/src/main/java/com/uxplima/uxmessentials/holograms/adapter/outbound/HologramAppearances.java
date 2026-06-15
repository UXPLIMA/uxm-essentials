package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.entity.Display;

import com.uxplima.uxmessentials.holograms.domain.Appearance;
import com.uxplima.uxmessentials.holograms.domain.Billboard;
import com.uxplima.uxmlib.hologram.Holograms;
import org.jspecify.annotations.NullMarked;

/**
 * The anti-corruption mapping from the domain {@link Appearance} (pure enums and primitives) onto uxmLib's
 * {@code Holograms.Builder} setters. The builder owns the native {@code TextDisplay} translation; this class
 * only bridges our value object to it, and maps our {@link Billboard} enum onto Paper's {@code Display.Billboard}.
 *
 * <p>A sentinel value ("no override") is skipped so the underlying display keeps Paper's default rather than a
 * magic literal. Background ARGB maps through {@link Color#fromARGB(int)}, brightness through a
 * {@link Display.Brightness} built from the block/sky channels (each defaulting to 0 when only the other is set).
 */
@NullMarked
final class HologramAppearances {

    private HologramAppearances() {}

    /** Apply every non-default field of {@code appearance} onto {@code builder}. */
    static void apply(Holograms.Builder builder, Appearance appearance) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(appearance, "appearance");
        builder.billboard(toBukkit(appearance.billboard()));
        builder.textShadow(appearance.textShadow());
        builder.scale(appearance.scale());
        builder.lineWidth(appearance.lineWidth());
        builder.viewRange(appearance.viewRange());
        if (appearance.hasBackground()) {
            builder.background(Color.fromARGB(appearance.backgroundArgb()));
        }
        if (appearance.hasBrightness()) {
            builder.brightness(brightness(appearance));
        }
    }

    /**
     * Apply the {@link Display}-shared fields of {@code appearance} — billboard, scale, view range and
     * brightness — onto an item or block {@code builder}. The text-only fields (background, line width, shadow)
     * have no meaning for an item or block display, so they are deliberately left off this path.
     */
    static void applyDisplay(Holograms.ModelBuilder<?> builder, Appearance appearance) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(appearance, "appearance");
        builder.billboard(toBukkit(appearance.billboard()));
        builder.scale(appearance.scale());
        builder.viewRange(appearance.viewRange());
        if (appearance.hasBrightness()) {
            builder.brightness(brightness(appearance));
        }
    }

    private static Display.Brightness brightness(Appearance appearance) {
        int block = Appearance.isDefaultBrightness(appearance.brightnessBlock()) ? 0 : appearance.brightnessBlock();
        int sky = Appearance.isDefaultBrightness(appearance.brightnessSky()) ? 0 : appearance.brightnessSky();
        return new Display.Brightness(block, sky);
    }

    static Display.Billboard toBukkit(Billboard billboard) {
        return switch (billboard) {
            case CENTER -> Display.Billboard.CENTER;
            case FIXED -> Display.Billboard.FIXED;
            case VERTICAL -> Display.Billboard.VERTICAL;
            case HORIZONTAL -> Display.Billboard.HORIZONTAL;
        };
    }
}
