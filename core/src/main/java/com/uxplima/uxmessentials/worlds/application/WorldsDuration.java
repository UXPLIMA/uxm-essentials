package com.uxplima.uxmessentials.worlds.application;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A length of time written in the worlds config: {@code 30s}, {@code 5m}, {@code 1h30m}, {@code 2d}, {@code 1w}. The
 * grammar is the one the other contexts read, but this context keeps its own copy rather than borrowing another
 * context's, as the moderation, custom command and playtime contexts do.
 */
final class WorldsDuration {

    private static final Pattern UNIT = Pattern.compile("(\\d{1,9})([smhdw])");

    private WorldsDuration() {}

    /** Parse {@code raw}, or empty when it is not a length of time. */
    static Optional<Duration> parse(String raw) {
        String text = raw.strip().toLowerCase(Locale.ROOT);
        Matcher matcher = UNIT.matcher(text);
        Duration total = Duration.ZERO;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) {
                return Optional.empty();
            }
            total = total.plus(
                    unit(Long.parseLong(matcher.group(1)), matcher.group(2).charAt(0)));
            end = matcher.end();
        }
        return end == 0 || end != text.length() ? Optional.empty() : Optional.of(total);
    }

    private static Duration unit(long amount, char unit) {
        return switch (unit) {
            case 's' -> Duration.ofSeconds(amount);
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            default -> Duration.ofDays(amount * 7);
        };
    }
}
