package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.domain.Warp;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** The holder for the warp manager GUI: carries the viewer and the listed warps snapshot for click routing. */
@NullMarked
public final class WarpManagerHolder implements InventoryHolder {

    private final PlayerRef viewer;
    private final List<Warp> warps;
    private @Nullable Inventory inventory;

    public WarpManagerHolder(PlayerRef viewer, List<Warp> warps) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.warps = List.copyOf(warps);
    }

    public PlayerRef viewer() {
        return viewer;
    }

    public List<Warp> warps() {
        return warps;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return Objects.requireNonNull(inventory, "inventory was not attached");
    }
}
