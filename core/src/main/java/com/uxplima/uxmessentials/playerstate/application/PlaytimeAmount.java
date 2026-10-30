package com.uxplima.uxmessentials.playerstate.application;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The length of time an operator types after {@code /playtime set}, {@code add} or {@code take}: {@code 30m},
 * {@code 2d5h}, {@code 1w}, or a bare {@code 0}. This context keeps its own grammar rather than borrowing another
 * context's, as the moderation and custom command contexts do.
 */
final class PlaytimeAmount {

    private static final Pattern UNIT = Pattern.compile("(\\d{1,9})([smhdw])");

    private PlaytimeAmount() {}

    /** Parse {@code raw}, or empty when it is not a length of time. */
    static Optional<Duration> parse(String raw) {
        String text = raw.strip().toLowerCase(Locale.ROOT);
        if (text.equals("0")) {
            return Optional.of(Duration.ZERO);
        }
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
