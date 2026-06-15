package com.uxplima.uxmessentials.holograms.adapter.inbound.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.google.common.base.Splitter;
import com.uxplima.uxmessentials.holograms.domain.Appearance;
import org.jspecify.annotations.NullMarked;

/**
 * Parses an operator's background-colour argument for {@code /hologram background} into a packed ARGB int (or
 * the {@link Appearance#DEFAULT_BACKGROUND} sentinel for {@code transparent}/{@code default}). Accepts a hex
 * {@code #aarrggbb} / {@code aarrggbb} / {@code #rrggbb} value, an {@code r,g,b} or {@code a,r,g,b} triple/quad,
 * a small set of named colours, and the {@code transparent} keyword. An unrecognised value returns empty so the
 * command boundary can reject it with {@code HOLOGRAM_BACKGROUND_INVALID} before any use case runs.
 */
@NullMarked
final class HologramColors {

    private static final int OPAQUE_ALPHA = 0xFF;
    private static final int BYTE_MASK = 0xFF;

    private static final Map<String, Integer> NAMED = Map.ofEntries(
            Map.entry("black", 0x000000),
            Map.entry("white", 0xFFFFFF),
            Map.entry("red", 0xFF0000),
            Map.entry("green", 0x00FF00),
            Map.entry("blue", 0x0000FF),
            Map.entry("yellow", 0xFFFF00),
            Map.entry("cyan", 0x00FFFF),
            Map.entry("magenta", 0xFF00FF),
            Map.entry("gray", 0x808080),
            Map.entry("grey", 0x808080),
            Map.entry("orange", 0xFFA500),
            Map.entry("purple", 0x800080),
            Map.entry("pink", 0xFFC0CB));

    private HologramColors() {}

    /** Parse {@code raw} to a packed ARGB int or the transparent sentinel, or empty when unrecognised. */
    static Optional<Integer> parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String value = raw.strip().toLowerCase(Locale.ROOT);
        if (value.equals("transparent") || value.equals("default") || value.equals("none")) {
            return Optional.of(Appearance.DEFAULT_BACKGROUND);
        }
        if (value.indexOf(',') >= 0) {
            return parseChannels(value);
        }
        if (NAMED.containsKey(value)) {
            return Optional.of(OPAQUE_ALPHA << 24 | NAMED.get(value));
        }
        return parseHex(value);
    }

    private static Optional<Integer> parseHex(String value) {
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (hex.length() != 6 && hex.length() != 8) {
            return Optional.empty();
        }
        try {
            long bits = Long.parseLong(hex, 16);
            int argb = hex.length() == 6 ? (OPAQUE_ALPHA << 24 | (int) bits) : (int) bits;
            return Optional.of(argb);
        } catch (NumberFormatException notHex) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> parseChannels(String value) {
        List<String> parts = Splitter.on(',').splitToList(value);
        if (parts.size() != 3 && parts.size() != 4) {
            return Optional.empty();
        }
        try {
            boolean quad = parts.size() == 4;
            int offset = quad ? 1 : 0;
            int alpha = quad ? channel(parts.get(0)) : OPAQUE_ALPHA;
            int r = channel(parts.get(offset));
            int g = channel(parts.get(offset + 1));
            int b = channel(parts.get(offset + 2));
            return Optional.of(alpha << 24 | r << 16 | g << 8 | b);
        } catch (NumberFormatException notNumeric) {
            return Optional.empty();
        }
    }

    private static int channel(String raw) {
        int value = Integer.parseInt(raw.strip());
        if (value < 0 || value > BYTE_MASK) {
            throw new NumberFormatException("channel out of 0-255: " + value);
        }
        return value;
    }
}
