package com.uxplima.uxmessentials.worlds.domain;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A square region of chunks centred on {@code (centerChunkX, centerChunkZ)} extending {@code radius}
 * chunks in every direction (Chebyshev radius). Its {@link #spiral()} iterator walks the region
 * outward ring by ring so the spawn area generates first.
 */
public record ChunkRegion(int centerChunkX, int centerChunkZ, int radius) {

    public ChunkRegion {
        if (radius < 0) {
            throw new IllegalArgumentException("radius must not be negative: " + radius);
        }
    }

    /** The number of chunks in the region: the area of the {@code (2*radius+1)} square. */
    public long totalChunks() {
        long side = 2L * radius + 1;
        return side * side;
    }

    /**
     * Yields every chunk in the region exactly once, ordered outward from the centre by Chebyshev
     * distance: ring 0 (the centre), then ring 1 (distance 1), up to ring {@code radius}. Positions
     * are produced lazily one ring at a time, so a large radius never materialises the whole region.
     */
    public Iterator<ChunkPos> spiral() {
        return new SpiralIterator();
    }

    /**
     * Streams the region ring by ring. Only the current ring's perimeter (at most {@code 8*radius}
     * positions) is held in memory at any time; rings are computed on demand as the cursor advances.
     */
    private final class SpiralIterator implements Iterator<ChunkPos> {

        private int ring = 0;
        private int indexInRing = 0;
        private List<ChunkPos> currentRing = ringPerimeter(0);

        @Override
        public boolean hasNext() {
            return ring <= radius && indexInRing < currentRing.size();
        }

        @Override
        public ChunkPos next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            ChunkPos pos = currentRing.get(indexInRing++);
            if (indexInRing >= currentRing.size() && ring < radius) {
                ring++;
                indexInRing = 0;
                currentRing = ringPerimeter(ring);
            }
            return pos;
        }
    }

    /** The chunks whose Chebyshev distance from the centre is exactly {@code distance}. */
    private List<ChunkPos> ringPerimeter(int distance) {
        List<ChunkPos> ring = new ArrayList<>(distance == 0 ? 1 : 8 * distance);
        for (int dx = -distance; dx <= distance; dx++) {
            for (int dz = -distance; dz <= distance; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == distance) {
                    ring.add(new ChunkPos(centerChunkX + dx, centerChunkZ + dz));
                }
            }
        }
        return ring;
    }
}
