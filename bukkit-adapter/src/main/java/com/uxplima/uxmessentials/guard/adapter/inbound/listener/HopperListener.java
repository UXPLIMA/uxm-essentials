package com.uxplima.uxmessentials.guard.adapter.inbound.listener;

import com.uxplima.uxmessentials.guard.adapter.GuardRuntime;
import com.uxplima.uxmessentials.guard.domain.BuiltInActions;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

/**
 * Hoppers, droppers and hopper minecarts moving items between containers. A move is refused when the destination
 * denies {@code world.hopper} or when it would carry items across a border, so a hopper outside a region cannot
 * drain a chest inside it and a chest inside cannot be filled from outside.
 */
public final class HopperListener implements Listener {

    private final GuardRuntime guard;

    public HopperListener(GuardRuntime guard) {
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        Location from = event.getSource().getLocation();
        Location to = event.getDestination().getLocation();
        if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return;
        }
        Block source = from.getBlock();
        Block target = to.getBlock();
        if (!guard.allowWorld(BuiltInActions.WORLD_HOPPER, source, target) || !guard.reachable(/* source= */ target, /* target= */ source)) {
            event.setCancelled(true);
        }
    }
}
