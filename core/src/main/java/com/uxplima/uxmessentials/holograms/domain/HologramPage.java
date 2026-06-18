package com.uxplima.uxmessentials.holograms.domain;

import java.util.List;
import java.util.Objects;

/**
 * One page of a multi-page {@link Hologram}: an ordered, non-empty list of {@link HologramLine}s shown as a
 * unit. A hologram with two or more pages shows one page per viewer at a time and a click cycles the viewer to
 * the next; a hologram with a single page behaves exactly like an ordinary text hologram. The line list is
 * copied defensively on construction so a stored page is immutable, mirroring {@link HologramContent}.
 *
 * @param lines the page's ordered text lines (at least one)
 */
public record HologramPage(List<HologramLine> lines) {

    public HologramPage {
        Objects.requireNonNull(lines, "lines");
        lines = List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("a hologram page needs at least one line");
        }
    }

    /** A page of the given ordered lines (at least one). */
    public static HologramPage of(List<HologramLine> lines) {
        return new HologramPage(lines);
    }

    /** The number of lines on this page. */
    public int lineCount() {
        return lines.size();
    }
}
