package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class KitCategoryParentSelectorHolder implements InventoryHolder {

    private final PlayerRef viewer;
    private final KitCategory category;
    private @Nullable Inventory inventory;

    public KitCategoryParentSelectorHolder(PlayerRef viewer, KitCategory category) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.category = Objects.requireNonNull(category, "category");
    }

    public PlayerRef viewer() {
        return viewer;
    }

    public KitCategory category() {
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
