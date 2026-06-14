package com.uxplima.uxmessentials.nametags.adapter.outbound;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import org.bukkit.Color;
import org.bukkit.entity.Display;

import com.uxplima.uxmessentials.nametags.domain.NametagAppearance;
import com.uxplima.uxmlib.hologram.Appearance;
import com.uxplima.uxmlib.hologram.Holograms;
import com.uxplima.uxmlib.hologram.Transform;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Maps the core {@link NametagAppearance} (operator strings and primitives, no rendering library) onto uxmLib's
 * {@link Appearance} (which is {@code org.bukkit.Color}-typed) plus its {@link Transform} for the text-display scale.
 * This is the adapter seam that keeps the appearance model itself in {@code :core} — the domain only validates the
 * structural and range invariants, and the platform interpretation lives here.
 *
 * <p>Tolerant throughout, by design: a server admin's typo in a colour, billboard, or out-of-range number must never
 * throw and blank every nametag. An unparseable billboard falls back to {@link Display.Billboard#CENTER}; an
 * unparseable colour is skipped (the platform default is kept) rather than substituted; the field stays at uxmLib's
 * "leave Paper's default" {@code null}.
 *
 * <h2>Background opacity</h2>
 *
 * uxmLib's {@link Appearance} has no separate background-opacity field — the background is one {@code org.bukkit.Color}
 * whose alpha channel <em>is</em> the opacity, and {@code textOpacity} is the unrelated text alpha. So an operator's
 * {@code background-opacity} is folded into the alpha of the background colour through {@link Color#fromARGB}: a
 * background colour with an opacity becomes an ARGB colour, a background colour without one keeps Paper's default
 * background alpha, and an opacity with no background colour is dropped (there is no colour to carry it). The scale is
 * the only transform set; rotation and translation stay at uxmLib's defaults.
 */
@NullMarked
public final class NametagAppearanceMapper {

    private NametagAppearanceMapper() {}

    /**
     * Apply {@code appearance} onto {@code builder} through the builder's fluent setters, skipping any field left at
     * the platform default. uxmLib's {@link Holograms.Builder} takes the styling field-by-field (it has no
     * whole-{@link Appearance} setter), so this funnels the mapped {@link #toAppearance(NametagAppearance) Appearance}
     * onto it; every {@code null}/default field is left untouched so Paper's default is kept.
     */
    public static Holograms.Builder applyTo(Holograms.Builder builder, NametagAppearance appearance) {
        Objects.requireNonNull(builder, "builder");
        Appearance mapped = toAppearance(appearance);
        builder.billboard(mapped.billboard()).seeThrough(mapped.seeThrough()).textShadow(mapped.textShadow());
        if (mapped.glow() != null) {
            builder.glow(mapped.glow());
        }
        if (mapped.background() != null) {
            builder.background(mapped.background());
        }
        if (mapped.lineWidth() != null) {
            builder.lineWidth(mapped.lineWidth());
        }
        if (mapped.viewRange() != null) {
            builder.viewRange(mapped.viewRange());
        }
        if (mapped.transform() != null) {
            builder.transform(mapped.transform());
        }
        return builder;
    }

    /** Build the uxmLib {@link Appearance} (with the scale transform folded in) for {@code appearance}. */
    public static Appearance toAppearance(NametagAppearance appearance) {
        Objects.requireNonNull(appearance, "appearance");
        Appearance mapped = Appearance.DEFAULT
                .withBillboard(billboard(appearance.billboard()))
                .withSeeThrough(appearance.seeThrough())
                .withTextShadow(appearance.textShadow())
                .withGlow(parseColor(appearance.glowColor()))
                .withBackground(background(appearance.backgroundColor(), appearance.backgroundOpacity()))
                .withLineWidth(boxed(appearance.lineWidth()))
                .withViewRange(viewRange(appearance.viewRange()))
                .withTransform(Transform.scale((float) appearance.scale()));
        return mapped;
    }

    /** Parse {@code raw} into a Paper billboard, tolerant of case and unknown values (→ {@link Display.Billboard#CENTER}). */
    static Display.Billboard billboard(String raw) {
        try {
            return Display.Billboard.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return Display.Billboard.CENTER;
        }
    }

    // The background colour carries the opacity in its alpha channel: an operator background-opacity overrides the
    // parsed colour's own alpha through fromARGB. With no background colour authored there is nothing to carry the
    // opacity, so it is dropped (and the platform default background is kept) rather than inventing a colour.
    private static @Nullable Color background(Optional<String> rawColor, OptionalInt opacity) {
        Color base = parseColor(rawColor);
        if (base == null) {
            return null;
        }
        if (opacity.isEmpty()) {
            return base;
        }
        return Color.fromARGB(clampByte(opacity.getAsInt()), base.getRed(), base.getGreen(), base.getBlue());
    }

    // Parse "#RRGGBB" or "#AARRGGBB" (the leading # optional) into a Bukkit Color, tolerant: a blank/absent/unparseable
    // value yields null so the caller leaves Paper's default rather than substituting a wrong colour.
    private static @Nullable Color parseColor(Optional<String> raw) {
        if (raw.isEmpty()) {
            return null;
        }
        String hex = raw.get().trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6 && hex.length() != 8) {
            return null;
        }
        try {
            long argb = Long.parseLong(hex, 16);
            if (hex.length() == 6) {
                // No alpha authored in the string: full opacity, so the colour alone shows.
                return Color.fromARGB(255, (int) (argb >> 16) & 0xFF, (int) (argb >> 8) & 0xFF, (int) argb & 0xFF);
            }
            return Color.fromARGB(
                    (int) (argb >> 24) & 0xFF, (int) (argb >> 16) & 0xFF, (int) (argb >> 8) & 0xFF, (int) argb & 0xFF);
        } catch (NumberFormatException notHex) {
            return null;
        }
    }

    private static @Nullable Integer boxed(OptionalInt value) {
        return value.isPresent() ? value.getAsInt() : null;
    }

    private static @Nullable Float viewRange(java.util.OptionalDouble value) {
        return value.isPresent() ? (float) value.getAsDouble() : null;
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
