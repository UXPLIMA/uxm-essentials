package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link InventoryHolder} that tags an online {@code /endersee} menu, so {@link EnderseeListener} can recognise
 * a click or close as belonging to one of these views (and never to a vanilla container the viewer happens to have
 * open) and read the target it mirrors. The holder is created first and the menu is built against it; {@link
 * #attach} then stores the built inventory so {@link #getInventory()} can answer it, the way Bukkit's holder
 * contract expects. Mirrors {@link InvseeHolder}, but for the target's ender chest, which is always editable.
 */
@NullMarked
final class EnderseeHolder implements InventoryHolder {

    private final PlayerRef target;
    private @Nullable Inventory inventory;

    EnderseeHolder(PlayerRef target) {
        this.target = Objects.requireNonNull(target, "target");
    }

    /** The player whose ender chest this menu mirrors and writes back to on close. */
    PlayerRef target() {
        return target;
    }

    /** Store the built menu so the holder contract can answer {@link #getInventory()}. */
    void attach(Inventory built) {
        this.inventory = Objects.requireNonNull(built, "built");
    }

    @Override
    public Inventory getInventory() {
        Inventory built = inventory;
        if (built == null) {
            throw new IllegalStateException("endersee inventory not attached yet");
        }
        return built;
    }
}
