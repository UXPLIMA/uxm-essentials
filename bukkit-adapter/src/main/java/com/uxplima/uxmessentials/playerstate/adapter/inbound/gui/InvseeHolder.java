package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link InventoryHolder} that tags a {@code /invsee} menu, so {@link InvseeListener} can recognise a click
 * or close as belonging to one of these views (and never to a vanilla container the viewer happens to have open)
 * and read the target it mirrors and whether the viewer may edit it. The holder is created first and the menu is
 * built against it; {@link #attach} then stores the built inventory so {@link #getInventory()} can answer it, the
 * way Bukkit's holder contract expects.
 */
@NullMarked
final class InvseeHolder implements InventoryHolder {

    private final PlayerRef target;
    private final boolean editable;
    private @Nullable Inventory inventory;

    InvseeHolder(PlayerRef target, boolean editable) {
        this.target = Objects.requireNonNull(target, "target");
        this.editable = editable;
    }

    /** The player whose inventory this menu mirrors and writes back to on close. */
    PlayerRef target() {
        return target;
    }

    /** Whether the viewer holds the modify node; a view-only menu cancels every click. */
    boolean editable() {
        return editable;
    }

    /** Store the built menu so the holder contract can answer {@link #getInventory()}. */
    void attach(Inventory built) {
        this.inventory = Objects.requireNonNull(built, "built");
    }

    @Override
    public Inventory getInventory() {
        Inventory built = inventory;
        if (built == null) {
            throw new IllegalStateException("invsee inventory not attached yet");
        }
        return built;
    }
}
