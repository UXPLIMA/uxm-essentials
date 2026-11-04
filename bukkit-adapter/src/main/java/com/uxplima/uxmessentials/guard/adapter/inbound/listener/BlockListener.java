package com.uxplima.uxmessentials.guard.adapter.inbound.listener;

import com.uxplima.uxmessentials.guard.adapter.ActionKinds;
import com.uxplima.uxmessentials.guard.adapter.Causes;
import com.uxplima.uxmessentials.guard.adapter.GuardRuntime;
import com.uxplima.uxmessentials.guard.domain.BuiltInActions;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

/**
 * Breaking and placing by players: blocks, multi-block placements, signs, buckets, hanging entities, vehicles and
 * other placed entities, fire started with a lighter, and bone meal. Registered while any rule uses
 * {@code break}, {@code place}, {@code item.use}, {@code hanging.place}, {@code vehicle.place} or {@code use.sign}.
 */
public final class BlockListener implements Listener {

    private final GuardRuntime guard;

    public BlockListener(GuardRuntime guard) {
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!guard.allowBlock(event.getPlayer(), BuiltInActions.BREAK, block, block.getType().name(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event instanceof BlockMultiPlaceEvent multi) {
            for (BlockState state : multi.getReplacedBlockStates()) {
                if (!guard.allowBlock(event.getPlayer(), BuiltInActions.PLACE, state.getBlock(),
                        event.getBlockPlaced().getType().name(), false)) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
        Block block = event.getBlockPlaced();
        if (!guard.allowBlock(event.getPlayer(), BuiltInActions.PLACE, block, block.getType().name(), false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        if (!guard.allow(event.getPlayer(), BuiltInActions.USE_SIGN, event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Block target = event.getBlock();
        if (!guard.allowItem(player, BuiltInActions.ITEM_USE_BUCKET, target.getLocation(), event.getBucket().name())
                || !guard.allowBlock(player, BuiltInActions.PLACE, target, event.getBucket().name(), false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Block target = event.getBlockClicked();
        if (!guard.allowItem(player, BuiltInActions.ITEM_USE_BUCKET, target.getLocation(), event.getBucket().name())
                || !guard.allowBlock(player, BuiltInActions.BREAK, target, target.getType().name(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        if (!guard.allow(event.getPlayer(), BuiltInActions.INTERACT_ANIMAL, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null && !guard.allow(player, BuiltInActions.HANGING_PLACE, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        boolean vehicle = event.getEntity() instanceof org.bukkit.entity.Vehicle;
        if (!guard.allow(player, vehicle ? BuiltInActions.VEHICLE_PLACE : BuiltInActions.PLACE,
                event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer() != null ? event.getPlayer() : Causes.player(event.getIgnitingEntity());
        if (player == null) {
            return;
        }
        if (!guard.allowItem(player, BuiltInActions.ITEM_USE_LIGHTER, event.getBlock().getLocation(),
                Material.FLINT_AND_STEEL.name())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        for (BlockState state : event.getBlocks()) {
            if (!guard.allow(player, ActionKinds.item(Material.BONE_MEAL), state.getBlock())) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
