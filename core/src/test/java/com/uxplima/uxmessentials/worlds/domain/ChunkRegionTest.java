package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

class ChunkRegionTest {

    @Test
    void totalChunksCountsTheSquare() {
        assertThat(new ChunkRegion(0, 0, 0).totalChunks()).isEqualTo(1L);
        assertThat(new ChunkRegion(0, 0, 1).totalChunks()).isEqualTo(9L);
        assertThat(new ChunkRegion(0, 0, 2).totalChunks()).isEqualTo(25L);
    }

    @Test
    void rejectsNegativeRadius() {
        assertThatThrownBy(() -> new ChunkRegion(0, 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void spiralForRadiusZeroYieldsOnlyTheCentre() {
        List<ChunkPos> positions = collect(new ChunkRegion(5, -3, 0));
        assertThat(positions).containsExactly(new ChunkPos(5, -3));
    }

    @Test
    void spiralYieldsEveryChunkExactlyOnce() {
        ChunkRegion region = new ChunkRegion(10, -4, 3);
        List<ChunkPos> positions = collect(region);

        assertThat(positions).hasSize((int) region.totalChunks());
        assertThat(new HashSet<>(positions)).hasSize(positions.size());
    }

    @Test
    void spiralStartsAtTheCentre() {
        List<ChunkPos> positions = collect(new ChunkRegion(10, -4, 3));
        assertThat(positions.get(0)).isEqualTo(new ChunkPos(10, -4));
    }

    @Test
    void spiralStaysWithinTheRegionBounds() {
        int cx = 10;
        int cz = -4;
        int r = 3;
        for (ChunkPos pos : collect(new ChunkRegion(cx, cz, r))) {
            assertThat(pos.x()).isBetween(cx - r, cx + r);
            assertThat(pos.z()).isBetween(cz - r, cz + r);
        }
    }

    @Test
    void spiralOrdersOutwardByChebyshevDistance() {
        int cx = 10;
        int cz = -4;
        int previous = -1;
        for (ChunkPos pos : collect(new ChunkRegion(cx, cz, 4))) {
            int distance = Math.max(Math.abs(pos.x() - cx), Math.abs(pos.z() - cz));
            assertThat(distance).isGreaterThanOrEqualTo(previous);
            previous = distance;
        }
    }

    private static List<ChunkPos> collect(ChunkRegion region) {
        List<ChunkPos> out = new ArrayList<>();
        Iterator<ChunkPos> it = region.spiral();
        while (it.hasNext()) {
            out.add(it.next());
        }
        return out;
    }
}
