package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class WarpEditorHolder implements InventoryHolder {
    private final PlayerRef viewer;
    private final String warpName;
    private final @Nullable PlayerRef warpOwner; // If null, it's a server warp. If not null, it's a player warp.
    private @Nullable Inventory inventory;

    public WarpEditorHolder(PlayerRef viewer, String warpName, @Nullable PlayerRef warpOwner) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.warpName = Objects.requireNonNull(warpName, "warpName");
        this.warpOwner = warpOwner;
    }

    public PlayerRef viewer() {
        return viewer;
    }

    public String warpName() {
        return warpName;
    }

    public @Nullable PlayerRef warpOwner() {
        return warpOwner;
    }

    public boolean isPlayerWarp() {
        return warpOwner != null;
    }

    public void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return Objects.requireNonNull(inventory, "inventory was not attached");
    }
}
