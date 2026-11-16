package com.uxplima.uxmessentials.shared.adapter.inbound.window;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmlib.gui.StorageGui;
import org.jspecify.annotations.NullMarked;

/**
 * Runs a confirmation once a uxmLib storage window is in front of its player. The window's open handler runs while
 * the open event is still being decided, and the window opens a tick late for a sleeping player, so the check waits
 * for the next tick and then looks at what the player has open: a cancelled open leaves their own inventory there.
 */
@NullMarked
public final class WindowShown {

    private WindowShown() {}

    /** Run {@code shown} at most once, after {@code gui} has opened for {@code player}. Call before opening it. */
    public static void confirm(StorageGui gui, Player player, Scheduler scheduler, Runnable shown) {
        Objects.requireNonNull(gui, "gui");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(shown, "shown");
        AtomicBoolean told = new AtomicBoolean();
        gui.onOpen(event -> {
            if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
                return;
            }
            scheduler.laterEntity(BukkitRefs.toRef(player), Duration.ZERO, () -> {
                if (showing(player, gui.getInventory()) && told.compareAndSet(false, true)) {
                    shown.run();
                }
            });
        });
    }

    /**
     * Whether {@code player} has {@code window} open now. Read right after an open on the player's thread, it says
     * whether the open went through: a cancelled one closes what was open before and leaves the player's own
     * inventory, and {@code openInventory} then still returns a view, that one, whenever another window was open.
     */
    public static boolean showing(Player player, Inventory window) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(window, "window");
        return player.getOpenInventory().getTopInventory().equals(window);
    }
}
