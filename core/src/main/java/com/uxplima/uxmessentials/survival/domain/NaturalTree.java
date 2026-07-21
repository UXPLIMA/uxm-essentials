package com.uxplima.uxmessentials.survival.domain;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The pure "is this a naturally grown tree?" test the tree-feller runs before it fells a connected group of logs, so a
 * player-built log wall, pillar, or house is broken one block at a time like vanilla rather than chain-felled. A grown
 * tree carries leaves the world placed; a placed log structure does not, so a naturally grown (non-persistent) leaf
 * sitting beside the connected logs is the signal that separates the two.
 *
 * <p>Pure: it walks abstract {@link BlockPos} coordinates through a caller-supplied predicate, so it is unit-testable
 * on a plain in-memory grid with no Bukkit in sight. The adapter supplies a predicate that resolves each coordinate to
 * a live block and reads its leaf tag and persistent flag (a persistent leaf is player-placed and never counts).
 */
public final class NaturalTree {

    private NaturalTree() {}

    /**
     * Whether the connected {@code logs} form a naturally grown tree: at least one coordinate in the full
     * 26-neighbourhood of some log holds a naturally grown leaf, as {@code isNaturalLeaf} decides. With no natural
     * leaf next to any log the group reads as placed logs, not a tree.
     *
     * @param logs the connected log coordinates, at least the origin
     * @param isNaturalLeaf whether a coordinate holds a naturally grown (non-persistent) leaf
     * @return whether a natural leaf sits next to the connected logs
     */
    public static boolean hasNaturalLeaf(Collection<BlockPos> logs, Predicate<BlockPos> isNaturalLeaf) {
        Objects.requireNonNull(logs, "logs");
        Objects.requireNonNull(isNaturalLeaf, "isNaturalLeaf");
        for (BlockPos log : logs) {
            if (naturalLeafBeside(log, isNaturalLeaf)) {
                return true;
            }
        }
        return false;
    }

    /** Whether any of the twenty-six neighbours of {@code log} is a naturally grown leaf. */
    private static boolean naturalLeafBeside(BlockPos log, Predicate<BlockPos> isNaturalLeaf) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx != 0 || dy != 0 || dz != 0) && isNaturalLeaf.test(log.offset(dx, dy, dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
