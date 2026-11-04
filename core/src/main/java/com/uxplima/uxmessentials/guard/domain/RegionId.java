package com.uxplima.uxmessentials.guard.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A region's name inside its world, lower case letters, digits, dash and underscore.
 *
 * @param value the canonical lower case id
 */
public record RegionId(String value) implements Comparable<RegionId> {

    private static final Pattern SHAPE = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    /** The id of a world's implicit default region, which a player can never define. */
    public static final RegionId WORLD_DEFAULTS = new RegionId("__world__");

    public RegionId {
        Objects.requireNonNull(value, "value");
        if (!value.equals("__world__") && !SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException("not a region id: " + value);
        }
    }

    /** Parse raw input, trimming and lower-casing it. */
    public static RegionId of(String raw) {
        return new RegionId(Objects.requireNonNull(raw, "raw").strip().toLowerCase(Locale.ROOT));
    }

    /** True when {@code raw} would parse. */
    public static boolean valid(String raw) {
        return SHAPE.matcher(raw.strip().toLowerCase(Locale.ROOT)).matches();
    }

    @Override
    public int compareTo(RegionId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
