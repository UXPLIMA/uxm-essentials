package com.uxplima.uxmessentials.guard.adapter.inbound.listener;

import com.uxplima.uxmessentials.guard.adapter.GuardRuntime;
import com.uxplima.uxmessentials.guard.adapter.Selections;
import com.uxplima.uxmessentials.guard.adapter.inbound.command.GuardWand;
import com.uxplima.uxmessentials.guard.application.GuardMessageKey;
import java.util.Map;
import java.util.Objects;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * The selection wand: left click marks the first corner (and starts a polygon), right click the second corner,
 * sneak right click adds a polygon point. Only players allowed to define regions can use it.
 */
public final class WandListener implements Listener {

    private final GuardRuntime guard;
    private final Selections selections;

    public WandListener(GuardRuntime guard, Selections selections) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.selections = Objects.requireNonNull(selections, "selections");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !GuardWand.is(event.getItem())) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null || !player.hasPermission("uxmessentials.guard.define")) {
            return;
        }
        event.setCancelled(true);
        Map<String, String> at = Map.of("x", String.valueOf(block.getX()), "y", String.valueOf(block.getY()),
                "z", String.valueOf(block.getZ()), "count", "");
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selections.first(player, block.getX(), block.getY(), block.getZ());
            guard.tell(player, GuardMessageKey.POS1_SET, at);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (player.isSneaking()) {
                int n = selections.point(player, block.getX(), block.getY(), block.getZ());
                guard.tell(player, GuardMessageKey.POINT_ADDED, Map.of("x", String.valueOf(block.getX()),
                        "y", String.valueOf(block.getY()), "z", String.valueOf(block.getZ()),
                        "count", String.valueOf(n)));
            } else {
                selections.second(player, block.getX(), block.getY(), block.getZ());
                guard.tell(player, GuardMessageKey.POS2_SET, at);
            }
        }
    }
}
