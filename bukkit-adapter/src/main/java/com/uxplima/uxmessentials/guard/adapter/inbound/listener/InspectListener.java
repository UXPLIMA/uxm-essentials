package com.uxplima.uxmessentials.guard.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import com.uxplima.uxmessentials.guard.adapter.GuardRuntime;

/**
 * Inspect mode: a click on a block explains who may do what there instead of doing anything. Registered for the
 * whole life of the module, since a player can turn inspect on in a world that has no rules yet.
 */
public final class InspectListener implements Listener {

    private final GuardRuntime guard;

    public InspectListener(GuardRuntime guard) {
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || event.getHand() != EquipmentSlot.HAND || !guard.inspecting(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        guard.inspectBlock(event.getPlayer(), block);
    }
}
