package com.uxplima.uxmessentials.worlds.domain;

import java.util.Locale;
import java.util.Optional;

/** The vanilla world-generation preset (maps to Bukkit {@code WorldType} in the adapter). */
public enum WorldGenType {
    NORMAL,
    FLAT,
    AMPLIFIED,
    LARGE_BIOMES;

    /** The preset {@code raw} names, ignoring case and surrounding space, or empty when it names none. */
    public static Optional<WorldGenType> parse(String raw) {
        String wanted = raw.strip().toUpperCase(Locale.ROOT);
        for (WorldGenType type : values()) {
            if (type.name().equals(wanted)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
