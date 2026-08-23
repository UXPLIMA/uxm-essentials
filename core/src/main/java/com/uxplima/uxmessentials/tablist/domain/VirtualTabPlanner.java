package com.uxplima.uxmessentials.tablist.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Pure exact-grid planner. Fixed slots win by construction; player groups are evaluated in design order and a
 * roster identity may occupy at most one cell, so overlapping conditions are deterministic rather than duplicating a
 * player. Every unclaimed slot becomes an explicit {@link VirtualTabCell.Empty}.
 */
public final class VirtualTabPlanner {

    private VirtualTabPlanner() {}

    public static <T, K> VirtualTabGrid<T> plan(
            TablistLayoutDesign design,
            Map<String, ? extends List<? extends T>> occupantsByGroup,
            Function<? super T, ? extends K> identity) {
        Objects.requireNonNull(design, "design");
        Objects.requireNonNull(occupantsByGroup, "occupantsByGroup");
        Objects.requireNonNull(identity, "identity");

        Map<Integer, VirtualTabCell.Content<T>> assignedCells = fixedCells(design.fixedSlots());
        Set<K> assignedRoster = new HashSet<>();
        Map<String, Integer> overflow = new LinkedHashMap<>();

        for (TablistPlayerGroup group : design.playerGroups()) {
            List<? extends T> configured = occupantsByGroup.get(group.id());
            List<? extends T> candidates = configured != null ? configured : List.of();
            placeGroup(group, candidates, identity, assignedRoster, assignedCells);
            int remaining = countRemaining(candidates, identity, assignedRoster);
            if (remaining > 0) {
                overflow.put(group.id(), remaining);
            }
        }

        List<VirtualTabCell<T>> cells = new ArrayList<>(design.slotCount());
        for (int slot = 1; slot <= design.slotCount(); slot++) {
            VirtualTabCell.Content<T> content = assignedCells.get(slot);
            cells.add(new VirtualTabCell<>(slot, content != null ? content : new VirtualTabCell.Empty<>()));
        }
        return new VirtualTabGrid<>(cells, overflow);
    }

    private static <T> Map<Integer, VirtualTabCell.Content<T>> fixedCells(List<TablistFiller> fixedSlots) {
        Map<Integer, VirtualTabCell.Content<T>> result = new HashMap<>();
        for (TablistFiller fixed : fixedSlots) {
            result.put(fixed.slot(), new VirtualTabCell.Fixed<>(fixed));
        }
        return result;
    }

    private static <T, K> void placeGroup(
            TablistPlayerGroup group,
            List<? extends T> candidates,
            Function<? super T, ? extends K> identity,
            Set<K> assignedRoster,
            Map<Integer, VirtualTabCell.Content<T>> assignedCells) {
        int candidateIndex = 0;
        for (Integer slot : group.slots()) {
            T candidate = null;
            while (candidateIndex < candidates.size() && candidate == null) {
                T next = Objects.requireNonNull(candidates.get(candidateIndex++), "player-group occupant");
                K key = Objects.requireNonNull(identity.apply(next), "player-group occupant identity");
                if (assignedRoster.add(key)) {
                    candidate = next;
                }
            }
            if (candidate == null) {
                return;
            }
            assignedCells.put(slot, new VirtualTabCell.Player<>(group.id(), candidate));
        }
    }

    private static <T, K> int countRemaining(
            List<? extends T> candidates, Function<? super T, ? extends K> identity, Set<K> assignedRoster) {
        Set<K> remaining = new LinkedHashSet<>();
        for (T candidate : candidates) {
            T present = Objects.requireNonNull(candidate, "player-group occupant");
            K key = Objects.requireNonNull(identity.apply(present), "player-group occupant identity");
            if (!assignedRoster.contains(key)) {
                remaining.add(key);
            }
        }
        return remaining.size();
    }
}
