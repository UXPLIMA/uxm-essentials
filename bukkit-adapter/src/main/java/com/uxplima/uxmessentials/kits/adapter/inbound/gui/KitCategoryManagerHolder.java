package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class KitCategoryManagerHolder implements InventoryHolder {

    private final PlayerRef viewer;
    private final List<KitCategory> categories;
    private @Nullable Inventory inventory;

    public KitCategoryManagerHolder(PlayerRef viewer, List<KitCategory> categories) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.categories = List.copyOf(categories);
    }

    public PlayerRef viewer() {
        return viewer;
    }

    public List<KitCategory> categories() {
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
