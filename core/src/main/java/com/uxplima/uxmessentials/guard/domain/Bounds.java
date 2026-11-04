package com.uxplima.uxmessentials.guard.domain;

/**
 * An axis aligned box of whole blocks, both corners inclusive.
 *
 * @param minX lowest x
 * @param minY lowest y
 * @param minZ lowest z
 * @param maxX highest x
 * @param maxY highest y
 * @param maxZ highest z
 */
public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** Every block a world can have. */
    public static final Bounds EVERYTHING = new Bounds(-30_000_000, -2048, -30_000_000, 30_000_000, 4096, 30_000_000);

    public Bounds {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("empty bounds");
        }
    }

    /** The box spanning two corners in any order. */
    public static Bounds of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Bounds(
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2));
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** True when {@code other} lies wholly inside this box. */
    public boolean contains(Bounds other) {
        return other.minX >= minX
                && other.maxX <= maxX
                && other.minY >= minY
                && other.maxY <= maxY
                && other.minZ >= minZ
                && other.maxZ <= maxZ;
    }

    public boolean intersects(Bounds other) {
        return other.minX <= maxX
                && other.maxX >= minX
                && other.minY <= maxY
                && other.maxY >= minY
                && other.minZ <= maxZ
                && other.maxZ >= minZ;
    }

    /** The number of blocks, saturating at {@link Long#MAX_VALUE}. */
    public long volume() {
        long dx = (long) maxX - minX + 1;
        long dy = (long) maxY - minY + 1;
        long dz = (long) maxZ - minZ + 1;
        double v = (double) dx * dy * dz;
        return v >= Long.MAX_VALUE ? Long.MAX_VALUE : dx * dy * dz;
    }

    /** This box grown by the given amounts on each side (negative shrinks); never inverted. */
    public Bounds expand(int west, int down, int north, int east, int up, int south) {
        return of(minX - west, minY - down, minZ - north, maxX + east, maxY + up, maxZ + south);
    }

    /** The smallest box holding both. */
    public Bounds union(Bounds other) {
        return new Bounds(
                Math.min(minX, other.minX),
                Math.min(minY, other.minY),
                Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX),
                Math.max(maxY, other.maxY),
                Math.max(maxZ, other.maxZ));
    }
}
