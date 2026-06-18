package com.uxplima.uxmessentials.worlds.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The on-disk folder name of a world, used as the stable registry identity (valid even while the
 * world is unloaded). Constrained to a safe folder-name shape so a name can never escape the worlds
 * container directory: no path separators, no traversal segments, no drive/scheme colon.
 */
public record WorldName(String value) {

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_-]*");
    private static final int MAX_LENGTH = 64;

    public WorldName {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("world name must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("world name must be at most " + MAX_LENGTH + " characters: " + value);
        }
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("world name must match " + VALID.pattern() + ": " + value);
        }
    }

    public static WorldName of(String value) {
        return new WorldName(value);
    }
}
