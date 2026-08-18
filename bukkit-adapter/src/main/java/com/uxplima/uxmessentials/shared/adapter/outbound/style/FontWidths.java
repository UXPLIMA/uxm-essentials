package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.util.Map;

import org.jspecify.annotations.NullMarked;

/**
 * How wide a string is when the client draws it in the default font, measured in pixels including the one-pixel
 * gap the client leaves after every glyph. Only the characters whose advance differs from the common six pixels
 * are listed; everything else, including the small-capital letters the interface is written in, is six.
 *
 * <p>This exists so a menu title can be centred (see {@link MenuTitles}). It is deliberately a table rather than
 * a measurement: the server has no font atlas to ask, and the default font's advances have been stable across
 * releases. A resource pack that replaces the font will shift a title by a few pixels, which is a cosmetic
 * offset rather than a defect.
 */
@NullMarked
final class FontWidths {

    private static final int DEFAULT_WIDTH = 6;

    private static final Map<Character, Integer> NARROW = Map.ofEntries(
            Map.entry(' ', 4),
            Map.entry('!', 2),
            Map.entry('"', 5),
            Map.entry('\'', 3),
            Map.entry('(', 5),
            Map.entry(')', 5),
            Map.entry('*', 5),
            Map.entry(',', 2),
            Map.entry('.', 2),
            Map.entry(':', 2),
            Map.entry(';', 2),
            Map.entry('<', 5),
            Map.entry('>', 5),
            Map.entry('[', 4),
            Map.entry(']', 4),
            Map.entry('`', 3),
            Map.entry('f', 5),
            Map.entry('i', 2),
            Map.entry('k', 5),
            Map.entry('l', 3),
            Map.entry('t', 4),
            Map.entry('|', 2),
            Map.entry('{', 5),
            Map.entry('}', 5),
            Map.entry('I', 4),
            Map.entry('@', 7));

    private FontWidths() {}

    /** The pixel width {@code text} occupies in the default font. */
    static int of(String text) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += NARROW.getOrDefault(text.charAt(i), DEFAULT_WIDTH);
        }
        return total;
    }
}
