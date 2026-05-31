package com.uxplima.uxmessentials.teleport.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * A block type's canonical name, normalised so an operator's {@code avoid-blocks} list matches regardless
 * of case. The teleport domain compares the material a random-teleport candidate would land on by this
 * value object rather than by a Bukkit {@code Material} enum, which keeps the safe-location policy pure;
 * the adapter maps the live block type to a {@code BlockTypeName} at the boundary.
 *
 * @param value the lower-cased material identifier (e.g. {@code lava}, {@code magma_block})
 */
public record BlockTypeName(String value) {

    public BlockTypeName {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("block type name must not be blank");
        }
    }

    /** Normalise any casing to the canonical lower-cased form. */
    public static BlockTypeName of(String raw) {
        return new BlockTypeName(Objects.requireNonNull(raw, "raw").trim().toLowerCase(Locale.ROOT));
    }
}
