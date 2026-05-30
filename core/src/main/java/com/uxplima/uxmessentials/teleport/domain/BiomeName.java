package com.uxplima.uxmessentials.teleport.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * A biome's canonical name, normalised so an operator's {@code excluded-biomes} list matches regardless
 * of case. The teleport domain compares biomes by this value object rather than by a Bukkit
 * {@code Biome} enum, which keeps the safe-location policy pure; the adapter maps the live biome to a
 * {@code BiomeName} at the boundary.
 *
 * @param value the lower-cased biome identifier (e.g. {@code ocean}, {@code deep_ocean})
 */
public record BiomeName(String value) {

    public BiomeName {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("biome name must not be blank");
        }
    }

    /** Normalise any casing to the canonical lower-cased form. */
    public static BiomeName of(String raw) {
        return new BiomeName(Objects.requireNonNull(raw, "raw").trim().toLowerCase(Locale.ROOT));
    }
}
