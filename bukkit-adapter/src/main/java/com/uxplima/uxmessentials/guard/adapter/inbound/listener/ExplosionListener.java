package com.uxplima.uxmessentials.guard.adapter.inbound.listener;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;

import com.uxplima.uxmessentials.guard.adapter.ActionKinds;
import com.uxplima.uxmessentials.guard.adapter.Causes;
import com.uxplima.uxmessentials.guard.adapter.GuardRuntime;
import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.BuiltInActions;

/**
 * Explosions remove only the blocks they may: each block is checked for the explosion's action and for the border
 * from where it went off, and for the player who lit or shot it. Entities hurt by a denied explosion take no damage.
 */
public final class ExplosionListener implements Listener {

    private final GuardRuntime guard;

    public ExplosionListener(GuardRuntime guard) {
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity source = event.getEntity();
        filter(ActionKinds.explosion(source), event.getLocation().getBlock(), event.blockList(), Causes.player(source));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        BlockState exploded = event.getExplodedBlockState();
        Material type = exploded.getType();
        ActionId action = Tag.BEDS.isTagged(type) || type == Material.RESPAWN_ANCHOR
                ? BuiltInActions.WORLD_EXPLOSION_BED
                : BuiltInActions.WORLD_EXPLOSION_OTHER;
        filter(action, event.getBlock(), event.blockList(), null);
    }

    private void filter(
            ActionId action, Block origin, List<Block> blocks, @org.jspecify.annotations.Nullable Player player) {
        blocks.removeIf(block -> !guard.allowWorld(action, origin, block)
                || (player != null
                        && !guard.allow(
                                player,
                                BuiltInActions.BREAK,
                                block.getWorld(),
                                block.getX(),
                                block.getY(),
                                block.getZ(),
                                false)));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }
        Entity victim = event.getEntity();
        if (victim instanceof Player || Causes.player(event.getDamager()) != null) {
            return;
        }
        ActionId action = ActionKinds.explosion(event.getDamager());
        Block at = victim.getLocation().getBlock();
        if (!guard.allowWorld(action, at.getWorld(), at.getX(), at.getY(), at.getZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHanging(HangingBreakEvent event) {
        if (event.getCause() != HangingBreakEvent.RemoveCause.EXPLOSION) {
            return;
        }
        Block at = event.getEntity().getLocation().getBlock();
        if (!guard.allowWorld(BuiltInActions.WORLD_EXPLOSION_OTHER, at.getWorld(), at.getX(), at.getY(), at.getZ())) {
            event.setCancelled(true);
        }
    }
}
