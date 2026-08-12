package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * The formatting helpers: placeholders that carry their own input in the key and render it, rather than
 * reading anything. They let an operator format a number, shorten a big one, spell a duration, or draw a bar
 * out of values another expansion produced, without a second plugin and without a script.
 *
 * <p>Every helper is total: an input that is not a number renders nothing, so a mistyped key degrades to the
 * dash the way an absent value does rather than throwing inside a scoreboard refresh.
 */
final class PlaceholderFormats {

    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ROOT);

    /** Compact scale suffixes, largest first, so the first one that fits wins. */
    private static final long[] SCALES = {1_000_000_000_000L, 1_000_000_000L, 1_000_000L, 1_000L};

    private static final String[] SUFFIXES = {"T", "B", "M", "k"};

    private static final int BAR_DEFAULT_LENGTH = 20;

    private static final int BAR_MAX_LENGTH = 100;

    /** Both glyphs live in the same Unicode block the vanilla font ships, so no resource pack is needed. */
    private static final char BAR_FILLED = '█';

    private static final char BAR_EMPTY = '░';

    private PlaceholderFormats() {}

    /** {@code 1234567.5} as {@code 1,234,567.5}: thousands grouped, at most two decimals, no trailing zeros. */
    static Optional<String> number(String raw) {
        return decimal(raw).map(value -> new DecimalFormat("#,##0.##", SYMBOLS).format(value));
    }

    /** {@code 1234567} as {@code 1.23M}: scaled to the largest suffix that fits, at most two decimals. */
    static Optional<String> compact(String raw) {
        Optional<BigDecimal> parsed = decimal(raw);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal value = parsed.get();
        BigDecimal magnitude = value.abs();
        for (int scale = 0; scale < SCALES.length; scale++) {
            BigDecimal unit = BigDecimal.valueOf(SCALES[scale]);
            if (magnitude.compareTo(unit) >= 0) {
                BigDecimal scaled = value.divide(unit, 2, RoundingMode.DOWN);
                return Optional.of(plain(scaled) + SUFFIXES[scale]);
            }
        }
        return Optional.of(plain(value.setScale(Math.min(2, Math.max(0, value.scale())), RoundingMode.DOWN)));
    }

    /** {@code 3725} seconds as {@code 1h2m5s}, in the same compact form every wait on the surface reads. */
    static Optional<String> time(String raw) {
        return whole(raw).map(seconds -> PlaceholderDurations.compact(Duration.ofSeconds(seconds)));
    }

    /**
     * Draw {@code now} out of {@code total} as a bar, from a tail of {@code <now>_<total>} or
     * {@code <now>_<total>_<length>}. The length defaults to twenty characters and is capped so a mistyped key
     * cannot ask for a line thousands of glyphs long. A non-positive total renders an empty bar rather than
     * dividing by zero, and progress beyond the total fills it rather than overflowing.
     */
    static Optional<String> progressBar(String tail) {
        String[] parts = tail.split("_", -1);
        if (parts.length < 2 || parts.length > 3) {
            return Optional.empty();
        }
        Optional<BigDecimal> now = decimal(parts[0]);
        Optional<BigDecimal> total = decimal(parts[1]);
        Optional<Long> length = parts.length == 3 ? whole(parts[2]) : Optional.of((long) BAR_DEFAULT_LENGTH);
        if (now.isEmpty() || total.isEmpty() || length.isEmpty()) {
            return Optional.empty();
        }
        int width = (int) Math.min(BAR_MAX_LENGTH, Math.max(0, length.get()));
        int filled = filled(now.get(), total.get(), width);
        return Optional.of(String.valueOf(BAR_FILLED).repeat(filled)
                + String.valueOf(BAR_EMPTY).repeat(width - filled));
    }

    private static int filled(BigDecimal now, BigDecimal total, int width) {
        if (total.signum() <= 0 || now.signum() <= 0) {
            return 0;
        }
        if (now.compareTo(total) >= 0) {
            return width;
        }
        return now.multiply(BigDecimal.valueOf(width))
                .divide(total, 0, RoundingMode.DOWN)
                .intValue();
    }

    /** The plain string of a decimal, without an exponent and without the trailing zeros scaling leaves. */
    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static Optional<BigDecimal> decimal(String raw) {
        try {
            return Optional.of(new BigDecimal(raw));
        } catch (NumberFormatException notANumber) {
            // A key that carries something other than a number renders nothing, the same as an absent value.
            return Optional.empty();
        }
    }

    private static Optional<Long> whole(String raw) {
        return decimal(raw).map(value -> value.setScale(0, RoundingMode.DOWN).longValue());
    }
}
