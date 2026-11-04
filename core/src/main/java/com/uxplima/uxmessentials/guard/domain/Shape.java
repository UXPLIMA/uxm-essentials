package com.uxplima.uxmessentials.guard.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The space a region covers. Each shape answers {@link #contains(int, int, int)} for a block without allocating,
 * and its {@link #bounds()} lets the compiler place it in sections.
 */
public sealed interface Shape {

    /** The smallest box that holds the shape. */
    Bounds bounds();

    /** True when the block at the given coordinates is inside. */
    boolean contains(int x, int y, int z);

    /** The kind word used in commands and storage. */
    String kind();

    /** How many blocks the shape covers, an estimate for round shapes; used to order overlapping regions. */
    long volume();

    /** True when every block of {@code box} is inside. Exact for boxes, conservative (may say false) otherwise. */
    default boolean fullyContains(Bounds box) {
        if (!bounds().contains(box)) {
            return false;
        }
        return contains(box.minX(), box.minY(), box.minZ())
                && contains(box.maxX(), box.maxY(), box.maxZ())
                && contains(box.minX(), box.minY(), box.maxZ())
                && contains(box.maxX(), box.minY(), box.minZ())
                && contains(box.minX(), box.maxY(), box.minZ())
                && contains(box.maxX(), box.maxY(), box.maxZ())
                && contains(box.minX(), box.maxY(), box.maxZ())
                && contains(box.maxX(), box.maxY(), box.minZ())
                && convexFullyContains(box);
    }

    /** Whether corner containment proves full containment; true only for convex shapes. */
    default boolean convexFullyContains(Bounds box) {
        return true;
    }

    /** A box from corner to corner. */
    record Cuboid(Bounds box) implements Shape {
        public Cuboid {
            Objects.requireNonNull(box, "box");
        }

        @Override
        public Bounds bounds() {
            return box;
        }

        @Override
        public boolean contains(int x, int y, int z) {
            return box.contains(x, y, z);
        }

        @Override
        public boolean fullyContains(Bounds other) {
            return box.contains(other);
        }

        @Override
        public String kind() {
            return "cuboid";
        }

        @Override
        public long volume() {
            return box.volume();
        }
    }

    /**
     * A polygon seen from above, extruded from {@code minY} to {@code maxY}. Points are block x and z; a block is
     * inside when its corner point lies inside the polygon or on its edge.
     */
    final class Polygon implements Shape {
        private final int[] xs;
        private final int[] zs;
        private final int minY;
        private final int maxY;
        private final Bounds box;

        private Polygon(int[] xs, int[] zs, int minY, int maxY, Bounds box) {
            if (xs.length != zs.length || xs.length < 3) {
                throw new IllegalArgumentException("a polygon needs at least three points");
            }
            this.xs = xs.clone();
            this.zs = zs.clone();
            this.minY = minY;
            this.maxY = maxY;
            this.box = box;
        }

        public int minY() {
            return minY;
        }

        public int maxY() {
            return maxY;
        }

        /** How many corners the polygon has. */
        public int points() {
            return xs.length;
        }

        /** A polygon over the given points. */
        public static Polygon of(int[] xs, int[] zs, int minY, int maxY) {
            int minX = Arrays.stream(xs).min().orElseThrow();
            int maxX = Arrays.stream(xs).max().orElseThrow();
            int minZ = Arrays.stream(zs).min().orElseThrow();
            int maxZ = Arrays.stream(zs).max().orElseThrow();
            return new Polygon(
                    xs,
                    zs,
                    Math.min(minY, maxY),
                    Math.max(minY, maxY),
                    new Bounds(minX, Math.min(minY, maxY), minZ, maxX, Math.max(minY, maxY), maxZ));
        }

        /** The corner x values, a copy. */
        public int[] xs() {
            return xs.clone();
        }

        /** The corner z values, a copy. */
        public int[] zs() {
            return zs.clone();
        }

        @Override
        public Bounds bounds() {
            return box;
        }

        @Override
        public boolean contains(int x, int y, int z) {
            if (!box.contains(x, y, z)) {
                return false;
            }
            boolean inside = false;
            int n = xs.length;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                int xi = xs[i];
                int zi = zs[i];
                int xj = xs[j];
                int zj = zs[j];
                if (onSegment(x, z, xi, zi, xj, zj)) {
                    return true;
                }
                if ((zi > z) != (zj > z)) {
                    long lhs = (long) (x - xi) * (zj - zi);
                    long rhs = (long) (xj - xi) * (z - zi);
                    if ((zj - zi) > 0 ? lhs < rhs : lhs > rhs) {
                        inside = !inside;
                    }
                }
            }
            return inside;
        }

        private static boolean onSegment(int x, int z, int x1, int z1, int x2, int z2) {
            long cross = (long) (x - x1) * (z2 - z1) - (long) (z - z1) * (x2 - x1);
            return cross == 0
                    && x >= Math.min(x1, x2)
                    && x <= Math.max(x1, x2)
                    && z >= Math.min(z1, z2)
                    && z <= Math.max(z1, z2);
        }

        @Override
        public boolean convexFullyContains(Bounds other) {
            // A polygon may be concave: corners inside do not prove the box is. Test the box edges along x and z.
            for (int x = other.minX(); x <= other.maxX(); x++) {
                if (!contains(x, other.minY(), other.minZ()) || !contains(x, other.minY(), other.maxZ())) {
                    return false;
                }
            }
            for (int z = other.minZ(); z <= other.maxZ(); z++) {
                if (!contains(other.minX(), other.minY(), z) || !contains(other.maxX(), other.minY(), z)) {
                    return false;
                }
            }
            return !edgeCrossesInto(other);
        }

        private boolean edgeCrossesInto(Bounds other) {
            for (int i = 0; i < xs.length; i++) {
                if (xs[i] > other.minX() && xs[i] < other.maxX() && zs[i] > other.minZ() && zs[i] < other.maxZ()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String kind() {
            return "polygon";
        }

        @Override
        public long volume() {
            long twiceArea = 0;
            for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
                twiceArea += (long) xs[j] * zs[i] - (long) xs[i] * zs[j];
            }
            return Math.max(1, Math.abs(twiceArea) / 2) * ((long) maxY - minY + 1);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Polygon p
                    && Arrays.equals(xs, p.xs)
                    && Arrays.equals(zs, p.zs)
                    && minY == p.minY
                    && maxY == p.maxY;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(xs), Arrays.hashCode(zs), minY, maxY);
        }

        @Override
        public String toString() {
            return "Polygon[" + xs.length + " points, y " + minY + ".." + maxY + "]";
        }
    }

    /** An upright cylinder (an ellipse seen from above) from {@code minY} to {@code maxY}. */
    record Cylinder(int centerX, int centerZ, int radiusX, int radiusZ, int minY, int maxY) implements Shape {
        public Cylinder {
            if (radiusX < 0 || radiusZ < 0 || minY > maxY) {
                throw new IllegalArgumentException("invalid cylinder");
            }
        }

        @Override
        public Bounds bounds() {
            return new Bounds(centerX - radiusX, minY, centerZ - radiusZ, centerX + radiusX, maxY, centerZ + radiusZ);
        }

        @Override
        public boolean contains(int x, int y, int z) {
            if (y < minY || y > maxY) {
                return false;
            }
            return ellipse((long) x - centerX, (long) z - centerZ, radiusX, radiusZ);
        }

        @Override
        public String kind() {
            return "cylinder";
        }

        @Override
        public long volume() {
            return Math.max(1, Math.round(Math.PI * (radiusX + 0.5) * (radiusZ + 0.5))) * ((long) maxY - minY + 1);
        }
    }

    /** A sphere around a block. */
    record Sphere(int centerX, int centerY, int centerZ, int radius) implements Shape {
        public Sphere {
            if (radius < 0) {
                throw new IllegalArgumentException("radius must not be negative");
            }
        }

        @Override
        public Bounds bounds() {
            return new Bounds(
                    centerX - radius,
                    centerY - radius,
                    centerZ - radius,
                    centerX + radius,
                    centerY + radius,
                    centerZ + radius);
        }

        @Override
        public boolean contains(int x, int y, int z) {
            long dx = (long) x - centerX;
            long dy = (long) y - centerY;
            long dz = (long) z - centerZ;
            double r = radius + 0.5;
            return dx * dx + dy * dy + dz * dz <= r * r;
        }

        @Override
        public String kind() {
            return "sphere";
        }

        @Override
        public long volume() {
            double r = radius + 0.5;
            return Math.max(1, Math.round(4.0 / 3.0 * Math.PI * r * r * r));
        }
    }

    /** A set of whole chunks from {@code minY} to {@code maxY}; keys are {@link #chunkKey(int, int)}. */
    record ChunkSet(Set<Long> chunks, int minY, int maxY, Bounds box) implements Shape {
        public ChunkSet {
            Objects.requireNonNull(chunks, "chunks");
            Objects.requireNonNull(box, "box");
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("a chunk set needs at least one chunk");
            }
            chunks = Set.copyOf(chunks);
        }

        /** A chunk set over the given chunk keys. */
        public static ChunkSet of(Set<Long> chunks, int minY, int maxY) {
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (long key : chunks) {
                int cx = chunkX(key);
                int cz = chunkZ(key);
                minX = Math.min(minX, cx);
                minZ = Math.min(minZ, cz);
                maxX = Math.max(maxX, cx);
                maxZ = Math.max(maxZ, cz);
            }
            int low = Math.min(minY, maxY);
            int high = Math.max(minY, maxY);
            return new ChunkSet(
                    chunks, low, high, new Bounds(minX << 4, low, minZ << 4, (maxX << 4) + 15, high, (maxZ << 4) + 15));
        }

        @Override
        public Bounds bounds() {
            return box;
        }

        @Override
        public boolean contains(int x, int y, int z) {
            return y >= minY && y <= maxY && chunks.contains(chunkKey(x >> 4, z >> 4));
        }

        @Override
        public boolean convexFullyContains(Bounds other) {
            for (int cx = other.minX() >> 4; cx <= other.maxX() >> 4; cx++) {
                for (int cz = other.minZ() >> 4; cz <= other.maxZ() >> 4; cz++) {
                    if (!chunks.contains(chunkKey(cx, cz))) {
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public String kind() {
            return "chunks";
        }

        @Override
        public long volume() {
            return (long) chunks.size() * 256 * ((long) maxY - minY + 1);
        }
    }

    /** The whole world. */
    record Global() implements Shape {
        @Override
        public Bounds bounds() {
            return Bounds.EVERYTHING;
        }

        @Override
        public boolean contains(int x, int y, int z) {
            return true;
        }

        @Override
        public boolean fullyContains(Bounds box) {
            return true;
        }

        @Override
        public String kind() {
            return "global";
        }

        @Override
        public long volume() {
            return Long.MAX_VALUE;
        }
    }

    /** Pack chunk coordinates into one key. */
    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    /** The chunk x of a key. */
    static int chunkX(long key) {
        return (int) (key >> 32);
    }

    /** The chunk z of a key. */
    static int chunkZ(long key) {
        return (int) key;
    }

    /** The shape kinds, for completion. */
    List<String> KINDS = List.of("cuboid", "polygon", "cylinder", "sphere", "chunks", "global");

    private static boolean ellipse(long dx, long dz, int rx, int rz) {
        double ax = rx + 0.5;
        double az = rz + 0.5;
        return (dx * dx) / (ax * ax) + (dz * dz) / (az * az) <= 1.0;
    }
}
