package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link InventoryHolder} that tags a {@code /kit show} preview menu, so {@link KitPreviewListener} can
 * recognise a click or close as belonging to one of these read-only views (and never to a vanilla container the
 * viewer happens to have open). The holder is created first and the menu is built against it; {@link #attach}
 * then stores the built inventory so {@link #getInventory()} can answer it, the way Bukkit's holder contract
 * expects. A preview window carries no mutable state: it is opened, every interaction with it is cancelled, and
 * it is discarded on close.
 */
@NullMarked
final class KitPreviewHolder implements InventoryHolder {

    private @Nullable Inventory inventory;

    /** Store the built menu so the holder contract can answer {@link #getInventory()}. */
    void attach(Inventory built) {
        this.inventory = Objects.requireNonNull(built, "built");
    }

    @Override
    public Inventory getInventory() {
        Inventory built = inventory;
        if (built == null) {
            throw new IllegalStateException("kit preview inventory not attached yet");
        }
        return built;
    }
}
