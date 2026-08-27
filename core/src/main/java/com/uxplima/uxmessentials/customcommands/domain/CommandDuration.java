package com.uxplima.uxmessentials.customcommands.domain;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * The duration grammar a command file is written in: {@code 30s}, {@code 5m}, {@code 1h30m}, {@code 2d}. Blank and
 * {@code 0} mean no wait, which is what an omitted cooldown or warmup reads as.
 *
 * <p>This context owns its own grammar rather than borrowing another's, following the moderation context: a
 * parser that four files depend on is cheaper to keep than a dependency edge between two bounded contexts.
 */
public final class CommandDuration {

    private static final Pattern UNIT = Pattern.compile("(\\d+)([smhdw])");

    private CommandDuration() {}

    /** Parse {@code raw}, or empty when it is not a duration at all. Blank and a bare zero parse to no wait. */
    public static Optional<Duration> parse(@Nullable String raw) {
        String trimmed = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty() || trimmed.equals("0")) {
            return Optional.of(Duration.ZERO);
        }
        Matcher matcher = UNIT.matcher(trimmed);
        Duration total = Duration.ZERO;
        int matchedEnd = 0;
        boolean any = false;
        while (matcher.find()) {
            if (matcher.start() != matchedEnd) {
                return Optional.empty();
            }
            total = total.plus(
                    unit(Long.parseLong(matcher.group(1)), matcher.group(2).charAt(0)));
            matchedEnd = matcher.end();
            any = true;
        }
        if (!any || matchedEnd != trimmed.length()) {
            return Optional.empty();
        }
        return Optional.of(total);
    }

    /** Render a span the way a file writes it, so a written definition reads back identically. */
    public static String format(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        if (seconds == 0) {
            return "0s";
        }
        StringBuilder out = new StringBuilder();
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        if (days > 0) {
            out.append(days).append('d');
        }
        if (hours > 0) {
            out.append(hours).append('h');
        }
        if (minutes > 0) {
            out.append(minutes).append('m');
        }
        if (seconds > 0) {
            out.append(seconds).append('s');
        }
        return out.toString();
    }

    private static Duration unit(long amount, char unit) {
        return switch (unit) {
            case 's' -> Duration.ofSeconds(amount);
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            case 'w' -> Duration.ofDays(amount * 7L);
            default -> Duration.ZERO;
        };
    }
}
