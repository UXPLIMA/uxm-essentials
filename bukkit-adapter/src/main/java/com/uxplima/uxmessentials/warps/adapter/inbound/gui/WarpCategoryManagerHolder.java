package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The holder for the warp category manager GUI: carries the viewer and the listed categories snapshot. */
@NullMarked
public final class WarpCategoryManagerHolder implements InventoryHolder {

    private final PlayerRef viewer;
    private final List<WarpCategory> categories;
    private @Nullable Inventory inventory;

    public WarpCategoryManagerHolder(PlayerRef viewer, List<WarpCategory> categories) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.categories = List.copyOf(categories);
    }

    public PlayerRef viewer() {
        return viewer;
    }

    public List<WarpCategory> categories() {
        return categories;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return Objects.requireNonNull(inventory, "inventory was not attached");
    }
}
