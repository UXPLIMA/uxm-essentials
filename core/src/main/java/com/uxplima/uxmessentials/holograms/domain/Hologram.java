package com.uxplima.uxmessentials.holograms.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;

/**
 * One server-wide hologram: a {@link HologramName}, the {@link Position} it floats at, its {@link HologramContent}
 * (the {@link HologramType} and what it renders — text lines, an item material, or a BlockData string), its visual
 * {@link Appearance}, its {@link Visibility}, its display {@link Rotation}, how often it re-renders, and the moment
 * it was created. A hologram is a value object — re-anchoring (a move), editing a line, restyling, or switching to
 * an item/block produces a new instance rather than mutating in place, so the aggregate is always in a valid state
 * and a repository save records a fully-formed snapshot.
 *
 * <p>The content fields are grouped into {@link HologramContent} to keep this aggregate small; {@code Hologram}
 * exposes the same content transitions and accessors it always did ({@code withLineAppended}, {@code asItem},
 * {@code type()}, {@code lines()}, …) and delegates each to the content value object, whose constructor enforces
 * the type invariants (a TEXT hologram keeps at least one line; an ITEM/BLOCK hologram carries its model string).
 * The legacy eleven-argument constructor is retained so the persistence mapper and field-by-field callers are
 * unaffected by the grouping.
 *
 * <p>The position carries its own {@link com.uxplima.uxmessentials.shared.domain.WorldRef}, so the hologram's
 * world is read from {@code location().world()}. {@link #refreshIntervalTicks()} is 0 for a static hologram
 * (rendered once, never re-rendered); a positive value means the live entity re-renders on that cadence so its
 * lines pick up fresh placeholder values. {@code createdAt} is preserved across every move or edit.
 *
 * <p>{@link #linkedNpcName()} is the optional name of an NPC the hologram follows (the FancyHolograms
 * link-with-NPC feature): when set, the renderer anchors the hologram above that NPC's head and re-anchors as it
 * moves, falling back to {@link #location()} when no such NPC exists; when null the hologram stays anchored to its
 * own stored location. It is a positioning concern only, so it lives on the aggregate as a plain name and is
 * preserved across every other edit.
 *
 * @param name the hologram's canonical, server-unique name
 * @param location where the hologram floats when it is not linked to an NPC
 * @param content the type and what it renders (text lines, item material, or block data)
 * @param appearance the visual styling (billboard, background, brightness, scale, …)
 * @param visibility who may see the hologram and how far away it stays visible
 * @param rotation the display's stored spin (yaw + pitch), only visible with a FIXED billboard
 * @param refreshIntervalTicks how often (in ticks) the live entity re-renders, or 0 for a static hologram
 * @param createdAt when the hologram was first created (preserved across a move or edit)
 * @param linkedNpcName the name of the NPC the hologram follows, or null when it is anchored to its own location
 */
public record Hologram(
        HologramName name,
        Position location,
        HologramContent content,
        Appearance appearance,
        Visibility visibility,
        Rotation rotation,
        int refreshIntervalTicks,
        Instant createdAt,
        @Nullable String linkedNpcName) {

    /** A refresh interval of 0 means "static": render once on enable, never re-render. */
    public static final int STATIC = 0;

    public Hologram {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(createdAt, "createdAt");
        if (refreshIntervalTicks < 0) {
            throw new IllegalArgumentException("refreshIntervalTicks must not be negative: " + refreshIntervalTicks);
        }
    }

    /**
     * The legacy field-by-field constructor, retained so the persistence mapper and field-level callers are
     * unaffected by the content grouping. The type and what it renders are folded into a {@link HologramContent},
     * which applies the same type invariants and defensive line copy it always did. The hologram is created
     * unlinked; the persistence mapper applies a stored {@link #linkedNpcName()} afterwards through
     * {@link #linkedTo(String)}, so a row threads the link through this same field-by-field path.
     */
    public Hologram(
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
        this(
                name,
                location,
                new HologramContent(type, lines, itemMaterial, blockData),
                appearance,
                visibility,
                rotation,
                refreshIntervalTicks,
                createdAt,
                null);
    }

    /**
     * A new TEXT hologram created now at {@code location} with the given ordered lines (at least one), the
     * default {@link Appearance}, visible to everyone, and no refresh interval (static).
     */
    public static Hologram create(HologramName name, Position location, List<HologramLine> lines, Instant createdAt) {
        return fresh(name, location, HologramContent.text(lines), createdAt);
    }

    /** A new ITEM hologram created now at {@code location} showing {@code itemMaterial}, with no lines. */
    public static Hologram createItem(HologramName name, Position location, String itemMaterial, Instant createdAt) {
        return fresh(name, location, HologramContent.item(itemMaterial), createdAt);
    }

    /** A new BLOCK hologram created now at {@code location} showing {@code blockData}, with no lines. */
    public static Hologram createBlock(HologramName name, Position location, String blockData, Instant createdAt) {
        return fresh(name, location, HologramContent.block(blockData), createdAt);
    }

    private static Hologram fresh(HologramName name, Position location, HologramContent content, Instant createdAt) {
        return new Hologram(
                name,
                location,
                content,
                Appearance.defaults(),
                Visibility.everyone(),
                Rotation.NONE,
                STATIC,
                createdAt,
                null);
    }

    // --- Content accessors (delegated, so existing call sites are unchanged) ---

    public HologramType type() {
        return content.type();
    }

    public List<HologramLine> lines() {
        return content.lines();
    }

    public @Nullable String itemMaterial() {
        return content.itemMaterial();
    }

    public @Nullable String blockData() {
        return content.blockData();
    }

    /** The number of lines this hologram renders (0 for an item/block hologram with no label lines). */
    public int lineCount() {
        return content.lineCount();
    }

    /** Whether this hologram re-renders on a cadence (a positive interval), rather than rendering once. */
    public boolean refreshes() {
        return refreshIntervalTicks > STATIC;
    }

    /** Whether this hologram follows an NPC rather than anchoring to its own stored location. */
    public boolean isLinked() {
        return linkedNpcName != null;
    }

    // --- Transitions (createdAt and the untouched fields are always preserved) ---

    /** A copy re-anchored to {@code newLocation}, keeping everything else (including any NPC link). */
    public Hologram movedTo(Position newLocation) {
        Objects.requireNonNull(newLocation, "newLocation");
        return new Hologram(
                name,
                newLocation,
                content,
                appearance,
                visibility,
                rotation,
                refreshIntervalTicks,
                createdAt,
                linkedNpcName);
    }

    /**
     * A copy linked to the NPC named {@code npcName}, so the renderer anchors it above that NPC and follows the
     * NPC as it moves; the hologram's own stored {@link #location()} is kept untouched so unlinking restores it.
     * Backs {@code /hologram linknpc}.
     */
    public Hologram linkedTo(String npcName) {
        Objects.requireNonNull(npcName, "npcName");
        return new Hologram(
                name, location, content, appearance, visibility, rotation, refreshIntervalTicks, createdAt, npcName);
    }

    /**
     * A copy with any NPC link cleared, so the hologram anchors to its own stored {@link #location()} again. Backs
     * {@code /hologram unlinknpc}; a no-op in effect on an already-unlinked hologram.
     */
    public Hologram unlinked() {
        return new Hologram(
                name, location, content, appearance, visibility, rotation, refreshIntervalTicks, createdAt, null);
    }

    /**
     * A full clone under {@code newName}, keeping every other property — location, content, appearance, visibility,
     * rotation, refresh interval, creation time and any NPC link. Backs {@code /hologram copy}: the duplicate is the
     * same hologram in every way but its name.
     */
    public Hologram renamedTo(HologramName newName) {
        Objects.requireNonNull(newName, "newName");
        return new Hologram(
                newName,
                location,
                content,
                appearance,
                visibility,
                rotation,
                refreshIntervalTicks,
                createdAt,
                linkedNpcName);
    }

    /** A copy with {@code line} appended after the current last line. */
    public Hologram withLineAppended(HologramLine line) {
        return withContent(content.withLineAppended(line));
    }

    /** A copy with the line at {@code index} replaced by {@code line}; rejects an out-of-range index. */
    public Hologram withLineReplaced(int index, HologramLine line) {
        return withContent(content.withLineReplaced(index, line));
    }

    /**
     * A copy with {@code line} inserted <em>before</em> the line at {@code index} (so the inserted line takes that
     * position and the rest shift down by one); an {@code index} at or past the current size appends. A negative
     * index is rejected.
     */
    public Hologram withLineInserted(int index, HologramLine line) {
        return withContent(content.withLineInserted(index, line));
    }

    /**
     * A copy with the line at {@code index} removed; rejects an out-of-range index, and (for a TEXT hologram)
     * rejects removing the last remaining line — a text hologram must keep at least one line, so the caller deletes
     * the hologram instead.
     */
    public Hologram withLineRemoved(int index) {
        return withContent(content.withLineRemoved(index));
    }

    /** A copy switched to an ITEM hologram showing {@code newItemMaterial} (lines and styling kept). */
    public Hologram asItem(String newItemMaterial) {
        return withContent(content.asItem(newItemMaterial));
    }

    /** A copy switched to a BLOCK hologram showing {@code newBlockData} (lines and styling kept). */
    public Hologram asBlock(String newBlockData) {
        return withContent(content.asBlock(newBlockData));
    }

    /** A copy restyled with {@code newAppearance}, keeping everything else. */
    public Hologram withAppearance(Appearance newAppearance) {
        Objects.requireNonNull(newAppearance, "newAppearance");
        return new Hologram(
                name,
                location,
                content,
                newAppearance,
                visibility,
                rotation,
                refreshIntervalTicks,
                createdAt,
                linkedNpcName);
    }

    /** A copy with a new {@link Visibility}, keeping everything else. */
    public Hologram withVisibility(Visibility newVisibility) {
        Objects.requireNonNull(newVisibility, "newVisibility");
        return new Hologram(
                name,
                location,
                content,
                appearance,
                newVisibility,
                rotation,
                refreshIntervalTicks,
                createdAt,
                linkedNpcName);
    }

    /** A copy with a new {@link Rotation}, keeping everything else. */
    public Hologram withRotation(Rotation newRotation) {
        Objects.requireNonNull(newRotation, "newRotation");
        return new Hologram(
                name,
                location,
                content,
                appearance,
                visibility,
                newRotation,
                refreshIntervalTicks,
                createdAt,
                linkedNpcName);
    }

    /** A copy that re-renders every {@code ticks} ticks (0 = static); rejects a negative interval. */
    public Hologram withRefreshIntervalTicks(int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("refreshIntervalTicks must not be negative: " + ticks);
        }
        return new Hologram(name, location, content, appearance, visibility, rotation, ticks, createdAt, linkedNpcName);
    }

    private Hologram withContent(HologramContent newContent) {
        return new Hologram(
                name,
                location,
                newContent,
                appearance,
                visibility,
                rotation,
                refreshIntervalTicks,
                createdAt,
                linkedNpcName);
    }
}
