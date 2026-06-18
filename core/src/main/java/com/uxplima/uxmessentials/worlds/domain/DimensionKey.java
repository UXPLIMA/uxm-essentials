package com.uxplima.uxmessentials.worlds.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A datapack custom-dimension key in {@code namespace:path} form. Stored on the spec so the
 * aggregate and schema are future-proof; full support is a later (Tier-3) sub-project.
 */
public record DimensionKey(String value) {

    private static final Pattern VALID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public DimensionKey {
        Objects.requireNonNull(value, "value");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("dimension key must be namespace:path: " + value);
        }
    }

    public static DimensionKey of(String value) {
        return new DimensionKey(value);
    }
}
