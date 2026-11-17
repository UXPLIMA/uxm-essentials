package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.NullMarked;

/**
 * Keeps PlaceholderAPI from reading a placeholder in text that is a value, not part of what the operator wrote: a
 * private message, a nickname, an item or sign name, a line relayed from another server. A line is filled with its
 * values and only then handed to PlaceholderAPI for the reader, so without this a player who types {@code %player_ip%}
 * has it filled in against whoever reads the line.
 *
 * <p>It works in two steps. {@link #shield} marks every {@code %} in a value by following it with a zero width
 * non-joiner, which the game's font draws with no width, so the reader sees the value as written. Then
 * {@link #around} wraps the call into PlaceholderAPI: every marked {@code %} is swapped for a token made for that one
 * call, and the token is swapped back in the answer. PlaceholderAPI therefore never sees a value's {@code %} at all: it
 * can neither open a placeholder nor close one the operator opened, whatever the template around it. The swap happens
 * only around that call, so nothing new reaches a log, Discord or a screen, and nothing an expansion answers, nor any
 * other text, can be mistaken for the token, since nobody outside the call knows it.
 */
@NullMarked
public final class PlaceholderShield {

    /** The zero width non-joiner, which the game's font draws with no width. */
    static final char BREAK = '\u200C';

    /** A Unicode noncharacter, never used in text, that opens and closes the token a marked {@code %} becomes. */
    static final char HIDDEN = '\uFDD0';

    private static final String MARKED = "%" + BREAK;

    private PlaceholderShield() {}

    /**
     * {@code value} with every {@code %} marked as a value's. A break the value starts with is dropped, since right after
     * the operator's own {@code %} it would mark that one instead; the noncharacter the call token is built from is
     * dropped first, so it cannot hide a break from that check. A value already shielded comes back unchanged.
     */
    public static String shield(String value) {
        Objects.requireNonNull(value, "value");
        String kept = value.indexOf(HIDDEN) < 0 ? value : value.replace(String.valueOf(HIDDEN), "");
        int start = 0;
        while (start < kept.length() && kept.charAt(start) == BREAK) {
            start++;
        }
        if (kept.indexOf('%', start) < 0) {
            return start == 0 ? kept : kept.substring(start);
        }
        StringBuilder shielded = new StringBuilder(kept.length() + 4);
        for (int i = start; i < kept.length(); i++) {
            char character = kept.charAt(i);
            shielded.append(character);
            boolean markedAlready = i + 1 < kept.length() && kept.charAt(i + 1) == BREAK;
            if (character == '%' && !markedAlready) {
                shielded.append(BREAK);
            }
        }
        return shielded.toString();
    }

    /**
     * {@code fill}, a call into PlaceholderAPI, made so that it never sees a value's {@code %}: every marked {@code %}
     * goes in as a token made for this call, and comes back marked in the answer. Every call into PlaceholderAPI with a
     * line that may hold values goes through here.
     */
    public static UnaryOperator<String> around(UnaryOperator<String> fill) {
        Objects.requireNonNull(fill, "fill");
        return text -> {
            if (!text.contains(MARKED)) {
                return fill.apply(text);
            }
            String token = HIDDEN + Long.toHexString(ThreadLocalRandom.current().nextLong()) + HIDDEN;
            return fill.apply(text.replace(MARKED, token)).replace(token, MARKED);
        };
    }

    /** {@code text} without the values' marked {@code %}: what PlaceholderAPI is shown of it. */
    public static String withoutValues(String text) {
        Objects.requireNonNull(text, "text");
        return text.contains(MARKED) ? text.replace(MARKED, "") : text;
    }

    /**
     * {@code text} with the marks taken off, for a command, an address or text to copy that a click carries: those are
     * not read on screen and have to stay byte for byte what was written.
     */
    public static String unshield(String text) {
        Objects.requireNonNull(text, "text");
        return text.contains(MARKED) ? text.replace(MARKED, "%") : text;
    }

    /** Whether {@code text} holds a marked {@code %}. */
    public static boolean marked(String text) {
        Objects.requireNonNull(text, "text");
        return text.contains(MARKED);
    }
}
