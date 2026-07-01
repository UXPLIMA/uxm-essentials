package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A whole menu parsed from its HOCON spec: a title, a row count, its refresh policy, the requirement that gates
 * opening, the actions run on open and close, the items keyed by their spec id, and an optional non-chest
 * inventory shape. Validated up front so the renderer can trust the row count and slot bounds without re-checking.
 *
 * <p>The {@code inventoryType} is carried as a plain operator token — {@code "hopper"}, {@code "dispenser"}, and so
 * on — never a Bukkit {@code InventoryType}, so this model stays pure and plain-JUnit testable. Absent, the menu is
 * the default {@code rows}-based chest. When present, the Bukkit-side façade maps the token to a real inventory
 * shape and falls back to a {@code rows}-based chest if that shape rejects a custom window, which is why {@code rows}
 * and its {@code 1..6} bound are still validated even for a non-chest menu: they size that fallback.
 */
public record MenuSpec(
        String title,
        int rows,
        RefreshSpec refresh,
        List<Ref> openRequirement,
        List<Ref> openActions,
        List<Ref> closeActions,
        Map<String, MenuItemSpec> items,
        Optional<String> inventoryType) {

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
        Objects.requireNonNull(inventoryType, "inventoryType");
        checkSlotsFit(items, rows);
    }

    /**
     * The historic seven-argument shape, kept so every existing {@code new MenuSpec(...)} call-site — the loader's
     * chest path and the engine's list/confirm/selector/editor holder specs — compiles unchanged. It delegates to the
     * canonical constructor with no inventory type, i.e. the default {@code rows}-based chest.
     */
    public MenuSpec(
            String title,
            int rows,
            RefreshSpec refresh,
            List<Ref> openRequirement,
            List<Ref> openActions,
            List<Ref> closeActions,
            Map<String, MenuItemSpec> items) {
        this(title, rows, refresh, openRequirement, openActions, closeActions, items, Optional.empty());
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
