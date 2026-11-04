package com.uxplima.uxmessentials.guard.domain.engine;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * One 16 block section of a chunk column. An exact cell is wholly inside each of its regions and answers with
 * its region set directly. A mixed cell is crossed by a region border: it tests its candidates and memoizes the
 * region set for each combination it meets.
 */
final class Cell {

    private final @Nullable RegionSet exact;
    private final CompiledRegion[] candidates;
    private volatile long[] memoKeys = new long[0];
    private volatile RegionSet[] memoSets = new RegionSet[0];

    private Cell(@Nullable RegionSet exact, CompiledRegion[] candidates) {
        this.exact = exact;
        this.candidates = candidates;
    }

    static Cell exact(RegionSet set) {
        return new Cell(set, new CompiledRegion[0]);
    }

    static Cell mixed(CompiledRegion[] candidates) {
        return new Cell(null, candidates);
    }

    RegionSet at(int x, int y, int z, WorldIndex index) {
        RegionSet set = exact;
        if (set != null) {
            return set;
        }
        if (candidates.length > 64) {
            return slow(x, y, z, index);
        }
        long mask = 0;
        for (int i = 0; i < candidates.length; i++) {
            if (candidates[i].region().shape().contains(x, y, z)) {
                mask |= 1L << i;
            }
        }
        long[] keys = memoKeys;
        RegionSet[] sets = memoSets;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == mask) {
                return sets[i];
            }
        }
        return remember(mask, index);
    }

    private synchronized RegionSet remember(long mask, WorldIndex index) {
        long[] keys = memoKeys;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == mask) {
                return memoSets[i];
            }
        }
        List<CompiledRegion> in = new ArrayList<>();
        for (int i = 0; i < candidates.length; i++) {
            if ((mask & (1L << i)) != 0) {
                in.add(candidates[i]);
            }
        }
        RegionSet set = index.intern(index.withDefaults(in.toArray(CompiledRegion[]::new)));
        long[] nextKeys = java.util.Arrays.copyOf(keys, keys.length + 1);
        RegionSet[] nextSets = java.util.Arrays.copyOf(memoSets, keys.length + 1);
        nextKeys[keys.length] = mask;
        nextSets[keys.length] = set;
        memoSets = nextSets;
        memoKeys = nextKeys;
        return set;
    }

    private RegionSet slow(int x, int y, int z, WorldIndex index) {
        List<CompiledRegion> in = new ArrayList<>();
        for (CompiledRegion candidate : candidates) {
            if (candidate.region().shape().contains(x, y, z)) {
                in.add(candidate);
            }
        }
        return index.intern(index.withDefaults(in.toArray(CompiledRegion[]::new)));
    }
}
