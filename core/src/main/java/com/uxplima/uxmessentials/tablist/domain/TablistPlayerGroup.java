package com.uxplima.uxmessentials.tablist.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One ordered player-placement group in a virtual layout. Conditions and sorting are compiled outside this value;
 * the planner receives the already-filtered, already-sorted occupants for {@link #id}.
 */
public record TablistPlayerGroup(String id, List<TablistSlotRange> ranges) {

    public TablistPlayerGroup {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ranges, "ranges");
        if (id.isBlank()) {
            throw new IllegalArgumentException("a tablist player group id cannot be blank");
        }
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("tablist player group '" + id + "' must own at least one slot range");
        }
        ranges = List.copyOf(ranges);
    }

    /** Every slot this group owns, preserving authored range order and ascending order inside each range. */
    public List<Integer> slots() {
        List<Integer> result = new ArrayList<>(capacity());
        for (TablistSlotRange range : ranges) {
            result.addAll(range.slots());
        }
        return List.copyOf(result);
    }

    public int capacity() {
        int result = 0;
        for (TablistSlotRange range : ranges) {
            result = Math.addExact(result, range.size());
        }
        return result;
    }
}
