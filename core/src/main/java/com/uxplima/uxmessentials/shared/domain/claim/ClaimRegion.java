package com.uxplima.uxmessentials.shared.domain.claim;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.WorldRef;

/**
 * An inclusive, block-space bounding box in a single world — the footprint a claim occupied at the moment it
 * was removed. Both corners are inclusive block coordinates: a one-block claim at (x, z) is
 * {@code minX == maxX == x} and {@code minZ == maxZ == z}, and a full 16×16 chunk at chunk (cx, cz) spans
 * {@code minX = cx<<4 .. maxX = (cx<<4)+15}.
 *
 * <p>Y is deliberately absent. A cascade consumer decides which warps sat inside a removed claim by their
 * horizontal position; claim plugins that model land as 2D columns (Lands, GriefPrevention) give no
 * meaningful vertical bound, so carrying one would only invite a false "outside the claim" answer.
 *
 * <p>The compact constructor <em>rejects</em> an inverted box rather than silently normalising it: the fields
 * are named {@code min}/{@code max}, so {@code minX > maxX} is a caller error, not a differently-ordered pair
 * of corners. Producers that receive two arbitrary corners are expected to pass them through
 * {@link Math#min}/{@link Math#max} before constructing, which they do — leaving this guard to catch a genuine
 * bug fast rather than to paper over it.
 *
 * @param world the world the box lies in
 * @param minX the inclusive lowest block X
 * @param minZ the inclusive lowest block Z
 * @param maxX the inclusive highest block X, not less than {@code minX}
 * @param maxZ the inclusive highest block Z, not less than {@code minZ}
 */
public record ClaimRegion(WorldRef world, int minX, int minZ, int maxX, int maxZ) {

    public ClaimRegion {
        Objects.requireNonNull(world, "world");
        if (minX > maxX) {
            throw new IllegalArgumentException("minX (" + minX + ") must not exceed maxX (" + maxX + ")");
        }
        if (minZ > maxZ) {
            throw new IllegalArgumentException("minZ (" + minZ + ") must not exceed maxZ (" + maxZ + ")");
        }
    }

    /**
     * Whether the block column at ({@code blockX}, {@code blockZ}) falls within this box, both bounds
     * inclusive. This is the horizontal containment test a cascade uses to decide whether a warp sat on the
     * removed land; provided here so consumers share one definition of "inside" rather than re-deriving the
     * comparison and disagreeing on inclusivity.
     */
    public boolean contains(int blockX, int blockZ) {
        return blockX >= minX && blockX <= maxX && blockZ >= minZ && blockZ <= maxZ;
    }
}
