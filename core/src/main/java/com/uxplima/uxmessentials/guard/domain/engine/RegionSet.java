package com.uxplima.uxmessentials.guard.domain.engine;

import com.uxplima.uxmessentials.guard.domain.BehaviourKey;
import com.uxplima.uxmessentials.guard.domain.Behaviours;
import com.uxplima.uxmessentials.guard.domain.GuardSubject;
import com.uxplima.uxmessentials.guard.domain.Region;
import com.uxplima.uxmessentials.guard.domain.Verdict;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The regions covering a point, in precedence order and split into tie groups, interned so every block with the
 * same regions shares one instance.
 *
 * <p>A tie group is a run of regions with the same priority where none contains another: inside a group a deny
 * beats an allow. The first group that says anything decides.
 */
public final class RegionSet {

    private final CompiledRegion[] regions;
    private final int[] groupEnds;
    private final long[] setActions;
    private final String[] families;
    private volatile @Nullable Behaviours behaviours;

    RegionSet(CompiledRegion[] ordered) {
        this.regions = ordered;
        this.groupEnds = groups(ordered);
        this.setActions = new long[ordered.length == 0 ? 0 : ordered[0].setActions().length];
        TreeSet<String> fams = new TreeSet<>();
        for (CompiledRegion region : ordered) {
            long[] bits = region.setActions();
            for (int i = 0; i < bits.length && i < setActions.length; i++) {
                setActions[i] |= bits[i];
            }
            if (!region.worldDefaults()) {
                fams.add(region.family());
            }
        }
        this.families = fams.toArray(String[]::new);
    }

    /** True when no region but the world defaults (if any) covers the point. */
    public boolean wilderness() {
        return families.length == 0;
    }

    /** The regions in precedence order, world defaults last. */
    public List<CompiledRegion> regions() {
        return List.of(regions);
    }

    /** The stored regions in precedence order, without the world defaults. */
    public List<Region> stored() {
        List<Region> out = new ArrayList<>(regions.length);
        for (CompiledRegion region : regions) {
            if (!region.worldDefaults()) {
                out.add(region.region());
            }
        }
        return List.copyOf(out);
    }

    /** The highest precedence stored region, if any. */
    public Optional<CompiledRegion> top() {
        for (CompiledRegion region : regions) {
            if (!region.worldDefaults()) {
                return Optional.of(region);
            }
        }
        return Optional.empty();
    }

    /** True when some region here sets {@code action}. */
    boolean sets(int action) {
        int word = action >>> 6;
        return word < setActions.length && (setActions[word] & (1L << action)) != 0;
    }

    /** The decision for {@code subject} doing {@code action} here. */
    Verdict decide(int action, GuardSubject subject) {
        if (!sets(action)) {
            return Verdict.UNSET;
        }
        int start = 0;
        for (int end : groupEnds) {
            boolean allow = false;
            for (int i = start; i < end; i++) {
                CompiledRegion region = regions[i];
                if (!region.sets(action)) {
                    continue;
                }
                Verdict verdict = region.decide(action, subject);
                if (verdict == Verdict.DENY) {
                    return Verdict.DENY;
                }
                allow |= verdict == Verdict.ALLOW;
            }
            if (allow) {
                return Verdict.ALLOW;
            }
            start = end;
        }
        return Verdict.UNSET;
    }

    /** The group boundaries, for the explainer. */
    int[] groupEnds() {
        return groupEnds;
    }

    CompiledRegion at(int index) {
        return regions[index];
    }

    int size() {
        return regions.length;
    }

    /**
     * The behaviours in force here: for each key, the value of the first region in precedence order that sets it.
     * Computed once per set.
     */
    public Behaviours behaviours() {
        Behaviours cached = behaviours;
        if (cached != null) {
            return cached;
        }
        Behaviours merged = Behaviours.empty();
        for (int i = regions.length - 1; i >= 0; i--) {
            merged = merged.overlay(regions[i].behaviours());
        }
        behaviours = merged;
        return merged;
    }

    /** One behaviour value in force here. */
    public Optional<String> behaviour(BehaviourKey key) {
        return behaviours().get(key);
    }

    /**
     * The border test: an effect from {@code source} may reach this set when this set is wilderness or the two
     * share a family.
     */
    public boolean reachableFrom(RegionSet source) {
        if (source == this || families.length == 0) {
            return true;
        }
        for (String family : families) {
            if (Arrays.binarySearch(source.families, family) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** True when both hold the same compiled regions. */
    public boolean sameRegions(RegionSet other) {
        return this == other || Arrays.equals(regions, other.regions);
    }

    private static int[] groups(CompiledRegion[] ordered) {
        List<Integer> ends = new ArrayList<>();
        int start = 0;
        for (int i = 1; i <= ordered.length; i++) {
            if (i == ordered.length || !sameGroup(ordered, start, i)) {
                ends.add(i);
                start = i;
            }
        }
        return ends.stream().mapToInt(Integer::intValue).toArray();
    }

    private static boolean sameGroup(CompiledRegion[] ordered, int start, int candidate) {
        CompiledRegion next = ordered[candidate];
        if (next.worldDefaults()) {
            return false;
        }
        for (int i = start; i < candidate; i++) {
            CompiledRegion member = ordered[i];
            if (member.worldDefaults() || member.region().priority() != next.region().priority()) {
                return false;
            }
            if (member.region().shape().bounds().contains(next.region().shape().bounds())
                    || next.region().shape().bounds().contains(member.region().shape().bounds())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "RegionSet" + Arrays.toString(regions);
    }
}
