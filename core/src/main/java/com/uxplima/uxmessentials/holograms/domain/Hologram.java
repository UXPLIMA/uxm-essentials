package com.uxplima.uxmessentials.holograms.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * One server-wide hologram: a {@link HologramName}, the {@link Position} it floats at, its ordered text
 * {@link HologramLine}s, its visual {@link Appearance}, how often it re-renders, and the moment it was created.
 * A hologram is a value object — re-anchoring (a move), editing a line, or restyling produces a new instance
 * rather than mutating in place, so the aggregate is always in a valid state and a repository save records a
 * fully-formed snapshot.
 *
 * <p>The position carries its own {@link com.uxplima.uxmessentials.shared.domain.WorldRef}, so the
 * hologram's world is read from {@code location().world()} rather than held separately. A hologram always
 * carries at least one line — an empty hologram would render nothing and is rejected at construction, so the
 * line-removal op refuses to drop the last line.
 *
 * <p>{@link #refreshIntervalTicks()} is 0 for a static hologram (rendered once, never re-rendered); a positive
 * value means the live entity re-renders on that cadence so its lines pick up fresh placeholder values. A line
 * that embeds no placeholder and a hologram with no interval cost nothing beyond the one initial render.
 *
 * @param name the hologram's canonical, server-unique name
 * @param location where the hologram floats
 * @param lines the ordered text lines (at least one), rendered top-down
 * @param appearance the visual styling (billboard, background, brightness, scale, …)
 * @param visibility who may see the hologram and how far away it stays visible
 * @param refreshIntervalTicks how often (in ticks) the live entity re-renders, or 0 for a static hologram
 * @param createdAt when the hologram was first created (preserved across a move or edit)
 */
public record Hologram(
        HologramName name,
        Position location,
        List<HologramLine> lines,
        Appearance appearance,
        Visibility visibility,
        int refreshIntervalTicks,
        Instant createdAt) {

    /** A refresh interval of 0 means "static": render once on enable, never re-render. */
    public static final int STATIC = 0;

    public Hologram {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(createdAt, "createdAt");
        lines = List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("a hologram needs at least one line");
        }
        if (refreshIntervalTicks < 0) {
            throw new IllegalArgumentException("refreshIntervalTicks must not be negative: " + refreshIntervalTicks);
        }
    }

    /**
     * A new hologram created now at {@code location} with the given ordered lines (at least one), the default
     * {@link Appearance}, visible to everyone, and no refresh interval (static).
     */
    public static Hologram create(HologramName name, Position location, List<HologramLine> lines, Instant createdAt) {
        return new Hologram(name, location, lines, Appearance.defaults(), Visibility.everyone(), STATIC, createdAt);
    }

    /** A copy re-anchored to {@code newLocation}, keeping everything else. */
    public Hologram movedTo(Position newLocation) {
        return new Hologram(
                name,
                Objects.requireNonNull(newLocation, "newLocation"),
                lines,
                appearance,
                visibility,
                refreshIntervalTicks,
                createdAt);
    }

    /** A copy with {@code line} appended after the current last line. */
    public Hologram withLineAppended(HologramLine line) {
        Objects.requireNonNull(line, "line");
        List<HologramLine> next = new ArrayList<>(lines);
        next.add(line);
        return new Hologram(name, location, next, appearance, visibility, refreshIntervalTicks, createdAt);
    }

    /** A copy with the line at {@code index} replaced by {@code line}; rejects an out-of-range index. */
    public Hologram withLineReplaced(int index, HologramLine line) {
        Objects.requireNonNull(line, "line");
        requireInRange(index);
        List<HologramLine> next = new ArrayList<>(lines);
        next.set(index, line);
        return new Hologram(name, location, next, appearance, visibility, refreshIntervalTicks, createdAt);
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
        return new Hologram(name, location, next, appearance, visibility, refreshIntervalTicks, createdAt);
    }

    /** A copy restyled with {@code newAppearance}, keeping the name, lines, visibility, interval and creation. */
    public Hologram withAppearance(Appearance newAppearance) {
        Objects.requireNonNull(newAppearance, "newAppearance");
        return new Hologram(name, location, lines, newAppearance, visibility, refreshIntervalTicks, createdAt);
    }

    /** A copy with a new {@link Visibility}, keeping the name, lines, appearance, interval and creation time. */
    public Hologram withVisibility(Visibility newVisibility) {
        Objects.requireNonNull(newVisibility, "newVisibility");
        return new Hologram(name, location, lines, appearance, newVisibility, refreshIntervalTicks, createdAt);
    }

    /** A copy that re-renders every {@code ticks} ticks (0 = static); rejects a negative interval. */
    public Hologram withRefreshIntervalTicks(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("refreshIntervalTicks must not be negative: " + ticks);
        }
        return new Hologram(name, location, lines, appearance, visibility, ticks, createdAt);
    }

    /** The number of lines this hologram renders (always at least one). */
    public int lineCount() {
        return lines.size();
    }

    /** Whether this hologram re-renders on a cadence (a positive interval), rather than rendering once. */
    public boolean refreshes() {
        return refreshIntervalTicks > STATIC;
    }

    private void requireInRange(int index) {
        if (index < 0 || index >= lines.size()) {
            throw new IndexOutOfBoundsException("line index out of range: " + index);
        }
    }
}
