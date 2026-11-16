package com.uxplima.uxmessentials.custommenus.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import com.uxplima.uxmessentials.shared.adapter.inbound.window.OpenWatch;
import com.uxplima.uxmlib.menu.runtime.MenuHolder;
import org.jspecify.annotations.NullMarked;

/**
 * Confirms a menu opened for somebody else once it is really in front of them. The menu is built off the tick and
 * opened on the target's thread later, and the open can still be refused there, by the menu's own rules or by another
 * plugin; so the sender is told from the open event, last, and only when nothing cancelled it.
 */
@NullMarked
public final class MenuOpenedListener implements Listener {

    private final OpenWatch<Inventory> watch = new OpenWatch<>(System::nanoTime);

    /** Run {@code shown} once the menu {@code menu} opens for {@code target}. Call before asking for the open. */
    public void expect(Player target, String menu, Runnable shown) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(menu, "menu");
        watch.expect(
                target.getUniqueId(),
                window -> window.getHolder(false) instanceof MenuHolder holder
                        && holder.specId().equals(menu),
                shown);
    }

    /** Last, cancelled or not, so the open is already decided. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent event) {
        watch.decided(event.getPlayer().getUniqueId(), event.getInventory(), event.isCancelled());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        watch.forget(event.getPlayer().getUniqueId());
    }
}
