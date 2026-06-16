package com.uxplima.uxmessentials.holograms.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;

/**
 * One server-wide hologram: a {@link HologramName}, the {@link Position} it floats at, its {@link HologramType},
 * its content (text {@link HologramLine}s for {@link HologramType#TEXT}, an item-material name for
 * {@link HologramType#ITEM}, a BlockData string for {@link HologramType#BLOCK}), its visual {@link Appearance},
 * how often it re-renders, and the moment it was created. A hologram is a value object — re-anchoring (a move),
 * editing a line, restyling, or switching to an item/block produces a new instance rather than mutating in
 * place, so the aggregate is always in a valid state and a repository save records a fully-formed snapshot.
 *
 * <p>The position carries its own {@link com.uxplima.uxmessentials.shared.domain.WorldRef}, so the
 * hologram's world is read from {@code location().world()} rather than held separately. A {@code TEXT} hologram
 * always carries at least one line — an empty text hologram would render nothing and is rejected at
 * construction, so the line-removal op refuses to drop the last line. An {@code ITEM} or {@code BLOCK}
 * hologram's content is its model (the {@link #itemMaterial()} or {@link #blockData()} string), so it needs no
 * line; the line invariant is therefore relaxed for the non-text types (lines, if present, are reserved for a
 * future text-label-above-the-model rendering — v1 renders the model only).
 *
 * <p>{@link #refreshIntervalTicks()} is 0 for a static hologram (rendered once, never re-rendered); a positive
 * value means the live entity re-renders on that cadence so its lines pick up fresh placeholder values. A line
 * that embeds no placeholder and a hologram with no interval cost nothing beyond the one initial render.
 *
 * @param name the hologram's canonical, server-unique name
 * @param location where the hologram floats
 * @param type whether the hologram renders text lines, a floating item, or a floating block
 * @param lines the ordered text lines (at least one for TEXT; may be empty for ITEM/BLOCK)
 * @param itemMaterial the {@code Material} name shown by an ITEM hologram, or {@code null} for other types
 * @param blockData the BlockData string shown by a BLOCK hologram, or {@code null} for other types
 * @param appearance the visual styling (billboard, background, brightness, scale, …)
 * @param visibility who may see the hologram and how far away it stays visible
 * @param rotation the display's stored spin (yaw + pitch), only visible with a FIXED billboard
 * @param refreshIntervalTicks how often (in ticks) the live entity re-renders, or 0 for a static hologram
 * @param createdAt when the hologram was first created (preserved across a move or edit)
 */
public record Hologram(
        HologramName name,
        Position location,
        HologramType type,
        List<HologramLine> lines,
        @Nullable String itemMaterial,
        @Nullable String blockData,
        Appearance appearance,
        Visibility visibility,
        Rotation rotation,
        int refreshIntervalTicks,
        Instant createdAt) {

    /** A refresh interval of 0 means "static": render once on enable, never re-render. */
    public static final int STATIC = 0;

    public Hologram {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(createdAt, "createdAt");
        lines = List.copyOf(lines);
        if (type.requiresLines() && lines.isEmpty()) {
            throw new IllegalArgumentException("a text hologram needs at least one line");
        }
        if (type == HologramType.ITEM && (itemMaterial == null || itemMaterial.isBlank())) {
            throw new IllegalArgumentException("an item hologram needs an item material");
        }
        if (type == HologramType.BLOCK && (blockData == null || blockData.isBlank())) {
            throw new IllegalArgumentException("a block hologram needs block data");
        }
        if (refreshIntervalTicks < 0) {
            throw new IllegalArgumentException("refreshIntervalTicks must not be negative: " + refreshIntervalTicks);
        }
    }

    /**
     * A new TEXT hologram created now at {@code location} with the given ordered lines (at least one), the
     * default {@link Appearance}, visible to everyone, and no refresh interval (static).
     */
    public static Hologram create(HologramName name, Position location, List<HologramLine> lines, Instant createdAt) {
        return new Hologram(
                name,
                location,
                HologramType.TEXT,
                lines,
                null,
                null,
                Appearance.defaults(),
                Visibility.everyone(),
                Rotation.NONE,
                STATIC,
                createdAt);
    }

    /** A new ITEM hologram created now at {@code location} showing {@code itemMaterial}, with no lines. */
    public static Hologram createItem(HologramName name, Position location, String itemMaterial, Instant createdAt) {
        return new Hologram(
                name,
                location,
                HologramType.ITEM,
                List.of(),
                Objects.requireNonNull(itemMaterial, "itemMaterial"),
                null,
                Appearance.defaults(),
                Visibility.everyone(),
                Rotation.NONE,
                STATIC,
                createdAt);
    }

    /** A new BLOCK hologram created now at {@code location} showing {@code blockData}, with no lines. */
    public static Hologram createBlock(HologramName name, Position location, String blockData, Instant createdAt) {
        return new Hologram(
                name,
                location,
                HologramType.BLOCK,
                List.of(),
                null,
                Objects.requireNonNull(blockData, "blockData"),
                Appearance.defaults(),
                Visibility.everyone(),
                Rotation.NONE,
                STATIC,
                createdAt);
    }

    /** A copy re-anchored to {@code newLocation}, keeping everything else. */
    public Hologram movedTo(Position newLocation) {
        return copy(Objects.requireNonNull(newLocation, "newLocation"), type, lines, itemMaterial, blockData);
    }

    /**
     * A full clone under {@code newName}, keeping every other property — location, type, lines, model,
     * appearance, visibility, rotation, refresh interval and creation time. Backs {@code /hologram copy}:
     * the duplicate is the same hologram in every way but its name.
     */
    public Hologram renamedTo(HologramName newName) {
        Objects.requireNonNull(newName, "newName");
        return new Hologram(
                newName,
                location,
                type,
                lines,
                itemMaterial,
                blockData,
                appearance,
                visibility,
                rotation,
                refreshIntervalTicks,
                createdAt);
    }

    /** A copy with {@code line} appended after the current last line. */
    public Hologram withLineAppended(HologramLine line) {
        Objects.requireNonNull(line, "line");
        List<HologramLine> next = new ArrayList<>(lines);
        next.add(line);
        return copy(location, type, next, itemMaterial, blockData);
    }

    /** A copy with the line at {@code index} replaced by {@code line}; rejects an out-of-range index. */
    public Hologram withLineReplaced(int index, HologramLine line) {
        Objects.requireNonNull(line, "line");
        requireInRange(index);
        List<HologramLine> next = new ArrayList<>(lines);
        next.set(index, line);
        return copy(location, type, next, itemMaterial, blockData);
    }

    /**
     * A copy with {@code line} inserted <em>before</em> the line at {@code index} (so the inserted line takes
     * that position and the rest shift down by one); an {@code index} at or past the current size appends, like
     * adding a line at the end. A negative index is rejected.
     */
    public Hologram withLineInserted(int index, HologramLine line) {
        Objects.requireNonNull(line, "line");
        if (index < 0) {
            throw new IndexOutOfBoundsException("line index must not be negative: " + index);
        }
        List<HologramLine> next = new ArrayList<>(lines);
        next.add(Math.min(index, next.size()), line);
        return copy(location, type, next, itemMaterial, blockData);
    }

    /**
     * A copy with the line at {@code index} removed; rejects an out-of-range index, and (for a TEXT hologram)
     * rejects removing the last remaining line — a text hologram must keep at least one line, so the caller
     * deletes the hologram instead.
     */
    public Hologram withLineRemoved(int index) {
        requireInRange(index);
        if (type.requiresLines() && lines.size() == 1) {
            throw new IllegalStateException("a text hologram must keep at least one line");
        }
        List<HologramLine> next = new ArrayList<>(lines);
        next.remove(index);
        return copy(location, type, next, itemMaterial, blockData);
    }

    /** A copy switched to an ITEM hologram showing {@code newItemMaterial} (lines and styling kept). */
    public Hologram asItem(String newItemMaterial) {
        Objects.requireNonNull(newItemMaterial, "newItemMaterial");
        return copy(location, HologramType.ITEM, lines, newItemMaterial, null);
    }

    /** A copy switched to a BLOCK hologram showing {@code newBlockData} (lines and styling kept). */
    public Hologram asBlock(String newBlockData) {
        Objects.requireNonNull(newBlockData, "newBlockData");
        return copy(location, HologramType.BLOCK, lines, null, newBlockData);
    }

    /** A copy restyled with {@code newAppearance}, keeping everything else. */
    public Hologram withAppearance(Appearance newAppearance) {
        Objects.requireNonNull(newAppearance, "newAppearance");
        return new Hologram(
                name,
                location,
                type,
                lines,
                itemMaterial,
                blockData,
                newAppearance,
                visibility,
                rotation,
                refreshIntervalTicks,
                createdAt);
    }

    /** A copy with a new {@link Visibility}, keeping everything else. */
    public Hologram withVisibility(Visibility newVisibility) {
        Objects.requireNonNull(newVisibility, "newVisibility");
        return new Hologram(
                name,
                location,
                type,
                lines,
                itemMaterial,
                blockData,
                appearance,
                newVisibility,
                rotation,
                refreshIntervalTicks,
                createdAt);
    }

    /** A copy with a new {@link Rotation}, keeping everything else. */
    public Hologram withRotation(Rotation newRotation) {
        Objects.requireNonNull(newRotation, "newRotation");
        return new Hologram(
                name,
                location,
                type,
                lines,
                itemMaterial,
                blockData,
                appearance,
                visibility,
                newRotation,
                refreshIntervalTicks,
                createdAt);
    }

    /** A copy that re-renders every {@code ticks} ticks (0 = static); rejects a negative interval. */
    public Hologram withRefreshIntervalTicks(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("refreshIntervalTicks must not be negative: " + ticks);
        }
        return new Hologram(
                name,
                location,
                type,
                lines,
                itemMaterial,
                blockData,
                appearance,
                visibility,
                rotation,
                ticks,
                createdAt);
    }

    /** The number of lines this hologram renders (0 for an item/block hologram with no label lines). */
    public int lineCount() {
        return lines.size();
    }

    /** Whether this hologram re-renders on a cadence (a positive interval), rather than rendering once. */
    public boolean refreshes() {
        return refreshIntervalTicks > STATIC;
    }

    private Hologram copy(
            Position at,
            HologramType newType,
            List<HologramLine> newLines,
            @Nullable String newItem,
            @Nullable String newBlock) {
        return new Hologram(
                name,
                at,
                newType,
                newLines,
                newItem,
                newBlock,
                appearance,
                visibility,
                rotation,
                refreshIntervalTicks,
                createdAt);
    }

    private void requireInRange(int index) {
        if (index < 0 || index >= lines.size()) {
            throw new IndexOutOfBoundsException("line index out of range: " + index);
        }
    }
}
