package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.NullMarked;

/**
 * Keeps PlaceholderAPI from reading a placeholder in text that is a value, not part of what the operator wrote: a
 * private message, a nickname, an item or sign name, a line relayed from another server. A line is filled with its
 * values and only then handed to PlaceholderAPI for the reader, so without this a player who types {@code %player_ip%}
 * has it filled in against whoever reads the line.
 *
 * <p>It works in two steps. {@link #shield} marks every {@code %} in a value by following it with a zero width
 * non-joiner, which the game's font draws with no width, so the reader sees the value as written. Then, at the call
 * into PlaceholderAPI, {@link #hide} swaps every marked {@code %} for a character PlaceholderAPI does not read, and
 * {@link #reveal} puts the marked {@code %} back in the answer. PlaceholderAPI therefore never sees a value's
 * {@code %} at all: it can neither open a placeholder nor close one the operator opened, whatever the template around
 * it. The swap happens only around that call, so nothing new reaches a log, Discord or a screen.
 */
@NullMarked
public final class PlaceholderShield {

    /** The zero width non-joiner, which the game's font draws with no width. */
    static final char BREAK = '‌';

    /** A Unicode noncharacter, never used in text, that stands for a marked {@code %} while PlaceholderAPI reads. */
    static final char HIDDEN = '﷐';

    private static final String MARKED = "%" + BREAK;

    private PlaceholderShield() {}

    /**
     * {@code value} with every {@code %} marked as a value's. A break the value starts with is dropped, since right after
     * the operator's own {@code %} it would mark that one instead; a value already shielded comes back unchanged.
     */
    public static String shield(String value) {
        Objects.requireNonNull(value, "value");
        int start = 0;
        while (start < value.length() && value.charAt(start) == BREAK) {
            start++;
        }
        if (start == 0 && value.indexOf('%') < 0 && value.indexOf(HIDDEN) < 0) {
            return value;
        }
        StringBuilder shielded = new StringBuilder(value.length() + 4);
        for (int i = start; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == HIDDEN) {
                continue;
            }
            shielded.append(character);
            boolean markedAlready = i + 1 < value.length() && value.charAt(i + 1) == BREAK;
            if (character == '%' && !markedAlready) {
                shielded.append(BREAK);
            }
        }
        return shielded.toString();
    }

    /**
     * {@code fill}, a call into PlaceholderAPI, made so that it never sees a value's {@code %}: the text goes in
     * {@link #hide hidden} and the answer comes back {@link #reveal revealed}. Every call into PlaceholderAPI with a line
     * that may hold values goes through here.
     */
    public static UnaryOperator<String> around(UnaryOperator<String> fill) {
        Objects.requireNonNull(fill, "fill");
        return text -> reveal(fill.apply(hide(text)));
    }

    /** {@code text} as PlaceholderAPI is to read it: every marked {@code %} out of its sight. */
    public static String hide(String text) {
        Objects.requireNonNull(text, "text");
        return text.contains(MARKED) ? text.replace(MARKED, String.valueOf(HIDDEN)) : text;
    }

    /** What PlaceholderAPI answered for a {@link #hide hidden} text, with every marked {@code %} back in place. */
    public static String reveal(String text) {
        Objects.requireNonNull(text, "text");
        return text.indexOf(HIDDEN) < 0 ? text : text.replace(String.valueOf(HIDDEN), MARKED);
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
