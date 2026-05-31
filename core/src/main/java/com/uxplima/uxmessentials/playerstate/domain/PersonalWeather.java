package com.uxplima.uxmessentials.playerstate.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * A per-player client-side weather for {@code /pweather}: {@code clear}, {@code rain}, or a {@code reset} that
 * clears the override and follows world weather again. Kept as a domain enum so the adapter maps to Bukkit's
 * {@code WeatherType} (or {@code resetPlayerWeather}) at the boundary.
 */
public enum PersonalWeather {
    CLEAR,
    RAIN,
    RESET;

    /** Parse a raw {@code /pweather} argument case-insensitively; empty when nothing matches. */
    public static Optional<PersonalWeather> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "clear", "sun", "sunny" -> Optional.of(CLEAR);
            case "rain", "storm", "downfall", "thunder" -> Optional.of(RAIN);
            case "reset", "off" -> Optional.of(RESET);
            default -> Optional.empty();
        };
    }
}
