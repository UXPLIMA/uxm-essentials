package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The holder for the warp → category selector GUI: carries the viewer and the name of the warp being assigned,
 * so a category click can save {@code warp.withCategoryId(...)} for that warp.
 */
@NullMarked
public final class WarpCategorySelectorHolder implements InventoryHolder {

    private final PlayerRef viewer;
    private final String warpName;
    private @Nullable Inventory inventory;

    public WarpCategorySelectorHolder(PlayerRef viewer, String warpName) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.warpName = Objects.requireNonNull(warpName, "warpName");
    }

    public PlayerRef viewer() {
        return viewer;
    }

    public String warpName() {
        return warpName;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return Objects.requireNonNull(inventory, "inventory was not attached");
    }
}
