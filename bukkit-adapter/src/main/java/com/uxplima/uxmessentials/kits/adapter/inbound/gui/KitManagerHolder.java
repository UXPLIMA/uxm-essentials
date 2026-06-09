package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link InventoryHolder} for the main Kit Manager GUI.
 */
@NullMarked
public final class KitManagerHolder implements InventoryHolder {

    private final PlayerRef viewer;
    private final List<KitDefinition> kits;
    private @Nullable Inventory inventory;

    public KitManagerHolder(PlayerRef viewer, List<KitDefinition> kits) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.kits = List.copyOf(kits);
    }

    public PlayerRef viewer() {
        return viewer;
    }

    public List<KitDefinition> kits() {
        return kits;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return Objects.requireNonNull(inventory, "inventory was not attached");
    }
}
