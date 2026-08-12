package com.uxplima.uxmessentials.playerstate.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * The colour of a player's glowing outline for {@code /glow [colour] [player]}. Vanilla draws the outline in the
 * colour of the scoreboard team the entity belongs to, so the palette is the sixteen colours a team can carry, plus
 * {@link #DEFAULT} for the uncoloured white outline a player gets when they belong to no team.
 *
 * <p>The enum lives in the domain because the use case decides what colour a player ends up with; the adapter maps a
 * constant to the Adventure/Bukkit colour when it assigns the team.
 */
public enum GlowColor {
    DEFAULT,
    BLACK,
    DARK_BLUE,
    DARK_GREEN,
    DARK_AQUA,
    DARK_RED,
    DARK_PURPLE,
    GOLD,
    GRAY,
    DARK_GRAY,
    BLUE,
    GREEN,
    AQUA,
    RED,
    LIGHT_PURPLE,
    YELLOW,
    WHITE;

    /** The lowercase, hyphen-free id an operator types, as {@code dark_aqua} for {@link #DARK_AQUA}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The colour for {@code id}, case-insensitively, or empty when no colour goes by that name. */
    public static Optional<GlowColor> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String wanted = id.strip().toLowerCase(Locale.ROOT);
        for (GlowColor colour : values()) {
            if (colour.id().equals(wanted)) {
                return Optional.of(colour);
            }
        }
        return Optional.empty();
    }
}
