package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.SmallCaps;
import org.jspecify.annotations.NullMarked;

/**
 * Writes a catalog template's prose in the small capitals the interface is drawn in, so a catalog file stays
 * plain readable text and the typography is applied once, when the language is loaded.
 *
 * <p>Three things are never touched. A MiniMessage tag ({@code <body>}, {@code <tag:'SKIN'>}) is markup, not
 * prose. A {@code {placeholder}} name has to keep its spelling or the substitution that follows will not find
 * it, and the value that replaces it arrives afterwards, which is why a player name still reads in ordinary
 * letters. Text inside {@code <plain>...</plain>} is left exactly as written, which is how a line says "these
 * letters are not prose": a command the player has to be able to type back ({@code /skin clear}), a permission
 * node, or a state word standing where a number usually stands ({@code free}, {@code public}).
 *
 * <p>There is deliberately no rule that guesses. The catalog writes {@code /rtp biome} in small capitals inside
 * one lore line and {@code /skin clear} in ordinary letters inside another, so any heuristic would be wrong
 * half the time; the line says which it means, and a translator keeps the tag where it stands.
 */
@NullMarked
public final class SmallCapsTemplates {

    /** The tag a catalog line wraps around text that must keep its own letters. */
    private static final String PLAIN_OPEN = "<plain>";

    private static final String PLAIN_CLOSE = "</plain>";

    private SmallCapsTemplates() {}

    /** {@code template} with its prose in small capitals, everything else exactly as it was written. */
    public static String apply(String template) {
        Objects.requireNonNull(template, "template");
        StringBuilder out = new StringBuilder(template.length());
        int index = 0;
        while (index < template.length()) {
            char current = template.charAt(index);
            if (template.startsWith(PLAIN_OPEN, index)) {
                int close = template.indexOf(PLAIN_CLOSE, index);
                int end = close < 0 ? template.length() : close + PLAIN_CLOSE.length();
                out.append(template, index, end);
                index = end;
            } else if (current == '<' || current == '{') {
                int end = span(template, index, current == '<' ? '>' : '}');
                out.append(template, index, end);
                index = end;
            } else {
                int end = prose(template, index);
                out.append(SmallCaps.of(template.substring(index, end)));
                index = end;
            }
        }
        return out.toString();
    }

    /** The end of the markup or placeholder span opened at {@code from}, past its closing character. */
    private static int span(String template, int from, char closing) {
        int close = template.indexOf(closing, from);
        return close < 0 ? template.length() : close + 1;
    }

    /** The end of the run of ordinary text starting at {@code from}. */
    private static int prose(String template, int from) {
        for (int index = from; index < template.length(); index++) {
            char current = template.charAt(index);
            if (current == '<' || current == '{') {
                return index;
            }
        }
        return template.length();
    }
}
