package com.uxplima.uxmessentials.shared.domain.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

class ClaimRegionTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    @Test
    void rejectsInvertedXBox() {
        assertThatThrownBy(() -> new ClaimRegion(WORLD, 10, 0, 5, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minX");
    }

    @Test
    void rejectsInvertedZBox() {
        assertThatThrownBy(() -> new ClaimRegion(WORLD, 0, 30, 20, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minZ");
    }

    @Test
    void acceptsDegenerateSingleBlockBox() {
        ClaimRegion region = new ClaimRegion(WORLD, 7, 7, 7, 7);
        assertThat(region.contains(7, 7)).isTrue();
        assertThat(region.contains(8, 7)).isFalse();
    }

    @Test
    void containsIsInclusiveOnAllFourEdges() {
        ClaimRegion region = new ClaimRegion(WORLD, 0, 0, 15, 15);
        assertThat(region.contains(0, 0)).isTrue();
        assertThat(region.contains(15, 15)).isTrue();
        assertThat(region.contains(0, 15)).isTrue();
        assertThat(region.contains(15, 0)).isTrue();
        assertThat(region.contains(16, 8)).isFalse();
        assertThat(region.contains(8, 16)).isFalse();
        assertThat(region.contains(-1, 8)).isFalse();
    }
}
