package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A whole menu parsed from its HOCON spec: a title, a row count, its refresh policy, the requirement that gates
 * opening, the actions run on open and close, and the items keyed by their spec id. Validated up front so the
 * renderer can trust the row count and slot bounds without re-checking.
 */
public record MenuSpec(
        String title,
        int rows,
        RefreshSpec refresh,
        List<Ref> openRequirement,
        List<Ref> openActions,
        List<Ref> closeActions,
        Map<String, MenuItemSpec> items) {

    public MenuSpec {
        Objects.requireNonNull(title, "title");
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be in 1..6: " + rows);
        }
        Objects.requireNonNull(refresh, "refresh");
        openRequirement = List.copyOf(Objects.requireNonNull(openRequirement, "openRequirement"));
        openActions = List.copyOf(Objects.requireNonNull(openActions, "openActions"));
        closeActions = List.copyOf(Objects.requireNonNull(closeActions, "closeActions"));
        items = Map.copyOf(Objects.requireNonNull(items, "items"));
        checkSlotsFit(items, rows);
    }

    private static void checkSlotsFit(Map<String, MenuItemSpec> items, int rows) {
        int capacity = rows * 9;
        for (MenuItemSpec item : items.values()) {
            for (int slot : item.slots().slots()) {
                if (slot >= capacity) {
                    throw new IllegalArgumentException("slot " + slot + " exceeds capacity " + capacity);
                }
            }
        }
    }
}
