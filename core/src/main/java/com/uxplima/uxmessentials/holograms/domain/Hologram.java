package com.uxplima.uxmessentials.holograms.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * One server-wide hologram: a {@link HologramName}, the {@link Position} it floats at, its ordered text
 * {@link HologramLine}s, and the moment it was created. A hologram is a value object — re-anchoring (a move)
 * or editing a line produces a new instance rather than mutating in place, so the aggregate is always in a
 * valid state and a repository save records a fully-formed snapshot.
 *
 * <p>The position carries its own {@link com.uxplima.uxmessentials.shared.domain.WorldRef}, so the
 * hologram's world is read from {@code location().world()} rather than held separately. A hologram always
 * carries at least one line — an empty hologram would render nothing and is rejected at construction, so the
 * line-removal op refuses to drop the last line.
 *
 * @param name the hologram's canonical, server-unique name
 * @param location where the hologram floats
 * @param lines the ordered text lines (at least one), rendered top-down
 * @param createdAt when the hologram was first created (preserved across a move or edit)
 */
public record Hologram(HologramName name, Position location, List<HologramLine> lines, Instant createdAt) {

    public Hologram {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(createdAt, "createdAt");
        lines = List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("a hologram needs at least one line");
        }
    }

    /** A new hologram created now at {@code location} with the given ordered lines (at least one). */
    public static Hologram create(HologramName name, Position location, List<HologramLine> lines, Instant createdAt) {
        return new Hologram(name, location, lines, createdAt);
    }

    /** A copy re-anchored to {@code newLocation}, keeping the name, lines, and original creation time. */
    public Hologram movedTo(Position newLocation) {
        return new Hologram(name, Objects.requireNonNull(newLocation, "newLocation"), lines, createdAt);
    }

    /** A copy with {@code line} appended after the current last line. */
    public Hologram withLineAppended(HologramLine line) {
        Objects.requireNonNull(line, "line");
        List<HologramLine> next = new ArrayList<>(lines);
        next.add(line);
        return new Hologram(name, location, next, createdAt);
    }

    /** A copy with the line at {@code index} replaced by {@code line}; rejects an out-of-range index. */
    public Hologram withLineReplaced(int index, HologramLine line) {
        Objects.requireNonNull(line, "line");
        requireInRange(index);
        List<HologramLine> next = new ArrayList<>(lines);
        next.set(index, line);
        return new Hologram(name, location, next, createdAt);
    }

    /**
     * A copy with the line at {@code index} removed; rejects an out-of-range index, and rejects removing the
     * last remaining line (a hologram must keep at least one line, so the caller deletes the hologram instead).
     */
    public Hologram withLineRemoved(int index) {
        requireInRange(index);
        if (lines.size() == 1) {
            throw new IllegalStateException("a hologram must keep at least one line");
        }
        List<HologramLine> next = new ArrayList<>(lines);
        next.remove(index);
        return new Hologram(name, location, next, createdAt);
    }

    /** The number of lines this hologram renders (always at least one). */
    public int lineCount() {
        return lines.size();
    }

    private void requireInRange(int index) {
        if (index < 0 || index >= lines.size()) {
            throw new IndexOutOfBoundsException("line index out of range: " + index);
        }
    }
}
