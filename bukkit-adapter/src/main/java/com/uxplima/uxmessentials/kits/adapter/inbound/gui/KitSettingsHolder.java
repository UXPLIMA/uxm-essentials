package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link InventoryHolder} for the Kit Settings GUI.
 */
@NullMarked
public final class KitSettingsHolder implements InventoryHolder {

    private final PlayerRef viewer;
    private final KitDefinition kit;
    private @Nullable Inventory inventory;

    public KitSettingsHolder(PlayerRef viewer, KitDefinition kit) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.kit = Objects.requireNonNull(kit, "kit");
    }

    public PlayerRef viewer() {
        return viewer;
    }

    public KitDefinition kit() {
        return kit;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return Objects.requireNonNull(inventory, "inventory was not attached");
    }
}
