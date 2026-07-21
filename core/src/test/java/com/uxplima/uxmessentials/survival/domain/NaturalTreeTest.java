package com.uxplima.uxmessentials.survival.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Pins {@link NaturalTree}: a connected group of logs reads as a naturally grown tree only when a natural leaf sits in
 * the immediate 26-neighbourhood of some log. A bare trunk (a player-built pillar or wall) has no adjacent natural
 * leaf, and the persistent (player-placed) leaves are excluded by the caller's predicate, so both look like placed
 * logs to the pure helper.
 */
class NaturalTreeTest {

    @Test
    void aTrunkWithAFaceAdjacentNaturalLeafIsATree() {
        List<BlockPos> logs = List.of(new BlockPos(0, 64, 0), new BlockPos(0, 65, 0), new BlockPos(0, 66, 0));
        Set<BlockPos> naturalLeaves = Set.of(new BlockPos(1, 66, 0)); // beside the crown

        assertThat(NaturalTree.hasNaturalLeaf(logs, naturalLeaves::contains)).isTrue();
    }

    @Test
    void aDiagonallyAdjacentNaturalLeafAlsoCounts() {
        List<BlockPos> logs = List.of(new BlockPos(0, 64, 0));
        Set<BlockPos> naturalLeaves = Set.of(new BlockPos(1, 65, 1)); // a full-26-neighbourhood diagonal

        assertThat(NaturalTree.hasNaturalLeaf(logs, naturalLeaves::contains)).isTrue();
    }

    @Test
    void aBareTrunkWithNoNaturalLeafIsNotATree() {
        List<BlockPos> logs = List.of(new BlockPos(0, 64, 0), new BlockPos(0, 65, 0), new BlockPos(0, 66, 0));

        // The predicate never accepts a coordinate: a placed pillar, or one wrapped only in persistent leaves the
        // adapter's predicate rejects, both surface here as "no natural leaf".
        assertThat(NaturalTree.hasNaturalLeaf(logs, pos -> false)).isFalse();
    }

    @Test
    void aNaturalLeafTwoBlocksAwayDoesNotCount() {
        List<BlockPos> logs = List.of(new BlockPos(0, 64, 0));
        Set<BlockPos> naturalLeaves = Set.of(new BlockPos(2, 64, 0)); // beyond the immediate neighbourhood

        assertThat(NaturalTree.hasNaturalLeaf(logs, naturalLeaves::contains)).isFalse();
    }
}
