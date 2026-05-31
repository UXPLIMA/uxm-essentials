package com.uxplima.uxmessentials.itemworld.domain;

import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * A validated weather request for {@code /weather <clear|rain|thunder> [duration]} and the {@code /sun} /
 * {@code /rain} / {@code /thunder} aliases. The kind is one of three states; the optional duration is a
 * non-negative second count an operator may pass to time-box the weather change.
 *
 * <p>Modelling weather as a closed enum keeps the {@link ItemWorldError#BAD_WEATHER} decision in the domain:
 * {@code /sun} is {@link Kind#CLEAR}, {@code /rain} is {@link Kind#RAIN}, {@code /thunder} is
 * {@link Kind#THUNDER}. The adapter translates the kind to the world's storm/thunder flags.
 *
 * @param kind the validated weather state to apply
 * @param durationSeconds the optional time-box in seconds, empty for the world default
 */
public record WeatherSpec(Kind kind, Optional<Integer> durationSeconds) {

    /** The three weather states the surface exposes. */
    public enum Kind {
        CLEAR,
        RAIN,
        THUNDER
    }

    public WeatherSpec {
        java.util.Objects.requireNonNull(kind, "kind");
        java.util.Objects.requireNonNull(durationSeconds, "durationSeconds");
        if (durationSeconds.isPresent() && durationSeconds.get() < 0) {
            throw new IllegalArgumentException("weather duration must be non-negative");
        }
    }

    /** A weather request with no explicit duration (the world default applies). */
    public static WeatherSpec of(Kind kind) {
        return new WeatherSpec(kind, Optional.empty());
    }

    /**
     * Parse a weather token ({@code clear}/{@code sun}, {@code rain}, {@code thunder}/{@code storm}) with an
     * optional non-negative duration. Returns empty for an unknown token or a negative duration; a use case
     * maps that to {@link ItemWorldError#BAD_WEATHER}.
     */
    public static Optional<WeatherSpec> parse(String rawKind, Optional<Integer> durationSeconds) {
        if (rawKind == null || durationSeconds == null) {
            return Optional.empty();
        }
        if (durationSeconds.isPresent() && durationSeconds.get() < 0) {
            return Optional.empty();
        }
        Kind kind = kind(rawKind.trim().toLowerCase(Locale.ROOT));
        return kind == null ? Optional.empty() : Optional.of(new WeatherSpec(kind, durationSeconds));
    }

    private static @Nullable Kind kind(String value) {
        return switch (value) {
            case "clear", "sun", "sunny" -> Kind.CLEAR;
            case "rain", "rainy", "snow" -> Kind.RAIN;
            case "thunder", "storm", "thunderstorm" -> Kind.THUNDER;
            default -> null;
        };
    }
}
