package com.uxplima.uxmessentials.security.adapter;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * The client brand a player's client reported, as staff are shown it. The client writes the brand, so it is taken as
 * text: a MiniMessage tag in it reads as the words, a {@code %placeholder%} in it is never filled (the message sink
 * fills PlaceholderAPI placeholders in a line before it parses it), and a control character is dropped. It is cut to
 * {@link #MAX_LENGTH} characters, which no real client brand reaches, so a hostile one cannot fill a staff member's
 * chat or outgrow a bus frame.
 */
@NullMarked
public final class ReportedBrand {

    /** The most characters of a reported brand that are kept. */
    public static final int MAX_LENGTH = 64;

    /** Stands in for a {@code %}, so PlaceholderAPI finds no placeholder while staff still read one. */
    private static final char PERCENT_SIGN = '％';

    private ReportedBrand() {}

    /** {@code reported}, cut, with control characters dropped, and safe to put into a line staff read. */
    public static String shown(String reported) {
        Objects.requireNonNull(reported, "reported");
        StringBuilder kept = new StringBuilder(Math.min(reported.length(), MAX_LENGTH));
        reported.codePoints()
                .filter(point -> !Character.isISOControl(point))
                .limit(MAX_LENGTH)
                .forEach(point -> kept.appendCodePoint(point == '%' ? PERCENT_SIGN : point));
        return kept.toString();
    }
}
