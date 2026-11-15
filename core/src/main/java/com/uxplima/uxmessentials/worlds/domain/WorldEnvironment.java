package com.uxplima.uxmessentials.worlds.domain;

import java.util.Locale;
import java.util.Optional;

/** The dimension flavour Bukkit creates the world with. */
public enum WorldEnvironment {
    NORMAL,
    NETHER,
    THE_END;

    /** The environment {@code raw} names, ignoring case and surrounding space, or empty when it names none. */
    public static Optional<WorldEnvironment> parse(String raw) {
        String wanted = raw.strip().toUpperCase(Locale.ROOT);
        for (WorldEnvironment environment : values()) {
            if (environment.name().equals(wanted)) {
                return Optional.of(environment);
            }
        }
        return Optional.empty();
    }
}
