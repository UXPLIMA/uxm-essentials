package com.uxplima.uxmessentials.poses.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Pins the Bukkit-free sit policy: which materials are sittable (wildcard and exact, case-insensitive), the
 * nominal seat offset per block kind, and whether a block's facing applies to the seat.
 */
class SittableBlocksTest {

    private final SittableBlocks blocks = new SittableBlocks(List.of("*_STAIRS", "*_SLAB", "*_CARPET", "BARREL"));

    @Test
    void matchesTrailingWildcardCaseInsensitively() {
        assertThat(blocks.isSittable("OAK_STAIRS")).isTrue();
        assertThat(blocks.isSittable("oak_slab")).isTrue();
        assertThat(blocks.isSittable("WHITE_CARPET")).isTrue();
    }

    @Test
    void matchesAnExactNameButNotAnUnlistedMaterial() {
        assertThat(blocks.isSittable("BARREL")).isTrue();
        assertThat(blocks.isSittable("STONE")).isFalse();
        assertThat(blocks.isSittable("OAK_PLANKS")).isFalse();
    }

    @Test
    void matchesALeadingWildcard() {
        SittableBlocks leading = new SittableBlocks(List.of("OAK_*"));
        assertThat(leading.isSittable("OAK_STAIRS")).isTrue();
        assertThat(leading.isSittable("BIRCH_STAIRS")).isFalse();
    }

    @Test
    void seatOffsetIsHalfABlockForStairsAndSlabs() {
        assertThat(blocks.seatOffset("OAK_STAIRS")).isEqualTo(0.5);
        assertThat(blocks.seatOffset("STONE_SLAB")).isEqualTo(0.5);
    }

    @Test
    void seatOffsetIsAThinLipForCarpetAndAFullBlockOtherwise() {
        assertThat(blocks.seatOffset("RED_CARPET")).isEqualTo(0.0625);
        assertThat(blocks.seatOffset("BARREL")).isEqualTo(1.0);
    }

    @Test
    void facingAppliesOnlyToStairs() {
        assertThat(blocks.facingApplies("OAK_STAIRS")).isTrue();
        assertThat(blocks.facingApplies("STONE_SLAB")).isFalse();
        assertThat(blocks.facingApplies("RED_CARPET")).isFalse();
    }
}
