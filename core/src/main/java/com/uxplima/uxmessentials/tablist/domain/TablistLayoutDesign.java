package com.uxplima.uxmessentials.tablist.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A compiled exact-grid layout design. Construction rejects every ambiguous ownership case up front: out-of-grid
 * slots, duplicate fixed cells, duplicate group ids, overlapping group ranges, and fixed/group collisions.
 */
public record TablistLayoutDesign(
        String id,
        int slotCount,
        TablistLayout.Direction direction,
        int gridRows,
        List<TablistFiller> fixedSlots,
        List<TablistPlayerGroup> playerGroups) {

    public static final int MAX_SLOTS = 80;

    public TablistLayoutDesign {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(fixedSlots, "fixedSlots");
        Objects.requireNonNull(playerGroups, "playerGroups");
        if (id.isBlank()) {
            throw new IllegalArgumentException("a tablist layout design id cannot be blank");
        }
        if (slotCount <= 0 || slotCount > MAX_SLOTS) {
            throw new IllegalArgumentException(
                    "tablist slot-count must be between 1 and " + MAX_SLOTS + ", got " + slotCount);
        }
        if (gridRows <= 0 || slotCount > TablistLayout.COLUMNS * gridRows) {
            throw new IllegalArgumentException("tablist grid rows cannot hold " + slotCount + " slots across "
                    + TablistLayout.COLUMNS + " columns");
        }
        fixedSlots = List.copyOf(fixedSlots);
        playerGroups = List.copyOf(playerGroups);
        validateOwnership(slotCount, fixedSlots, playerGroups);
    }

    /** The packet list-order value for one exact slot in this design. */
    public int listOrder(int slot) {
        requireInside(slotCount, slot, "slot");
        return TablistLayout.slotToListOrder(slot, direction, gridRows);
    }

    private static void validateOwnership(
            int slotCount, List<TablistFiller> fixedSlots, List<TablistPlayerGroup> groups) {
        Map<Integer, String> owners = new HashMap<>();
        for (TablistFiller fixed : fixedSlots) {
            int slot = fixed.slot();
            requireInside(slotCount, slot, "fixed slot");
            claim(owners, slot, "fixed slot " + slot);
        }

        Map<String, Boolean> groupIds = new HashMap<>();
        for (TablistPlayerGroup group : groups) {
            if (groupIds.put(group.id(), Boolean.TRUE) != null) {
                throw new IllegalArgumentException("duplicate tablist player group id '" + group.id() + "'");
            }
            for (Integer slot : group.slots()) {
                requireInside(slotCount, slot, "player-group slot");
                claim(owners, slot, "player group '" + group.id() + "'");
            }
        }
    }

    private static void claim(Map<Integer, String> owners, int slot, String owner) {
        String previous = owners.putIfAbsent(slot, owner);
        if (previous != null) {
            throw new IllegalArgumentException(
                    "tablist slot " + slot + " is owned by both " + previous + " and " + owner);
        }
    }

    private static void requireInside(int slotCount, int slot, String label) {
        if (slot <= 0 || slot > slotCount) {
            throw new IllegalArgumentException(label + " " + slot + " is outside the 1-" + slotCount + " layout grid");
        }
    }
}
