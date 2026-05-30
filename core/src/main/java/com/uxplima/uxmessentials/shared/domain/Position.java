package com.uxplima.uxmessentials.shared.domain;

import java.util.Objects;

/**
 * An immutable location in a world: coordinates plus the look direction.
 *
 * <p>This is the kernel's geometry primitive — the teleport context's destinations, the {@code /back}
 * capture, and the pre-warmed RTP queue all carry {@code Position} rather than a Bukkit
 * {@code Location}. The adapter maps to and from {@code Location} at the boundary. {@code yaw} and
 * {@code pitch} are degrees; block coordinates (used by the move-cancels-warmup comparison) are
 * derived via {@link #blockX()} / {@link #blockY()} / {@link #blockZ()} so a sub-block jitter does
 * not register as movement.
 *
 * @param world the world this position lives in
 * @param x world x coordinate
 * @param y world y coordinate
 * @param z world z coordinate
 * @param yaw horizontal look angle in degrees
 * @param pitch vertical look angle in degrees
 */
public record Position(WorldRef world, double x, double y, double z, float yaw, float pitch) {

    public Position {
        Objects.requireNonNull(world, "world");
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        requireFinite(yaw, "yaw");
        requireFinite(pitch, "pitch");
    }

    /** A position with a neutral look direction, for destinations that do not carry an orientation. */
    public static Position of(WorldRef world, double x, double y, double z) {
        return new Position(world, x, y, z, 0f, 0f);
    }

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    /** True when both positions resolve to the same world and block cell, ignoring look direction. */
    public boolean sameBlock(Position other) {
        Objects.requireNonNull(other, "other");
        return world.equals(other.world)
                && blockX() == other.blockX()
                && blockY() == other.blockY()
                && blockZ() == other.blockZ();
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite: " + value);
        }
    }

    private static void requireFinite(float value, String field) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite: " + value);
        }
    }
}
