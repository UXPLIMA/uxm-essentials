package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * Keeps PlaceholderAPI from reading a placeholder in text that is a value, not part of what the operator wrote: a
 * private message, a nickname, an item or sign name, a line relayed from another server. A line is filled with its
 * values and only then handed to PlaceholderAPI for the reader, so without this a player who types {@code %player_ip%}
 * has it filled in against whoever reads the line.
 *
 * <p>Every {@code %} in the value is followed by a zero width non-joiner. PlaceholderAPI takes the text after a
 * {@code %} up to the first {@code _} as the name of the expansion to ask, and no expansion is named with that
 * character in front, so the placeholder is left as typed. The character has no width in the game's font, so the
 * reader sees the value exactly as it was written. A value already shielded is returned unchanged.
 */
@NullMarked
public final class PlaceholderShield {

    /** The zero width non-joiner, which the game's font draws with no width. */
    static final char BREAK = '‌';

    private PlaceholderShield() {}

    /** {@code value} with no placeholder PlaceholderAPI would fill; the same text when it holds no {@code %}. */
    public static String shield(String value) {
        Objects.requireNonNull(value, "value");
        int first = value.indexOf('%');
        if (first < 0) {
            return value;
        }
        StringBuilder shielded = new StringBuilder(value.length() + 4);
        shielded.append(value, 0, first);
        for (int i = first; i < value.length(); i++) {
            char character = value.charAt(i);
            shielded.append(character);
            boolean brokenAlready = i + 1 < value.length() && value.charAt(i + 1) == BREAK;
            if (character == '%' && !brokenAlready) {
                shielded.append(BREAK);
            }
        }
        return shielded.toString();
    }
}
