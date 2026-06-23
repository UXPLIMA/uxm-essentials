package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The holder for a single warp category's settings GUI: carries the viewer and the category being edited. */
@NullMarked
public final class WarpCategorySettingsHolder implements InventoryHolder {

    private final PlayerRef viewer;
    private final WarpCategory category;
    private @Nullable Inventory inventory;

    public WarpCategorySettingsHolder(PlayerRef viewer, WarpCategory category) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.category = Objects.requireNonNull(category, "category");
    }

    public PlayerRef viewer() {
        return viewer;
    }

    public WarpCategory category() {
        return category;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return Objects.requireNonNull(inventory, "inventory was not attached");
    }
}
