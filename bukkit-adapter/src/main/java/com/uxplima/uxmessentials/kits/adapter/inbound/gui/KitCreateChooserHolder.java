package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class KitCreateChooserHolder implements InventoryHolder {

    private final PlayerRef viewer;
    private @Nullable Inventory inventory;

    public KitCreateChooserHolder(PlayerRef viewer) {
        this.viewer = viewer;
    }

    public PlayerRef viewer() {
        return viewer;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("inventory not attached");
        }
        return inventory;
    }
}
