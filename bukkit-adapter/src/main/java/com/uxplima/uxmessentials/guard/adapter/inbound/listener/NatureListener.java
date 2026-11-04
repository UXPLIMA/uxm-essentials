package com.uxplima.uxmessentials.guard.adapter.inbound.listener;

import com.uxplima.uxmessentials.guard.adapter.Causes;
import com.uxplima.uxmessentials.guard.adapter.GuardRuntime;
import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.BuiltInActions;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;

/**
 * What nature does to blocks: liquids, fire, growth, spread, decay, forming and melting, pistons, dispensers,
 * portals, lightning and mob grief. Each is a {@code world.*} action decided against {@code everyone}; effects that
 * travel from one block to another also pass the border test, so nothing crosses into a region of another family.
 */
public final class NatureListener implements Listener {

    private final GuardRuntime guard;

    public NatureListener(GuardRuntime guard) {
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Block from = event.getBlock();
        Block to = event.getToBlock();
        ActionId action = from.getType() == Material.DRAGON_EGG ? BuiltInActions.WORLD_DRAGON_EGG
                : from.getType() == Material.LAVA ? BuiltInActions.WORLD_LIQUID_LAVA : BuiltInActions.WORLD_LIQUID_WATER;
        if (!guard.allowWorld(action, from, to)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        Block target = event.getBlock();
        Material type = event.getNewState().getType();
        ActionId action;
        if (Tag.FIRE.isTagged(type)) {
            action = BuiltInActions.WORLD_FIRE_SPREAD;
        } else if (type == Material.VINE || type == Material.CAVE_VINES || type == Material.TWISTING_VINES
                || type == Material.WEEPING_VINES || type == Material.GLOW_LICHEN) {
            action = BuiltInActions.WORLD_GROW_VINE;
        } else if (type.name().contains("SCULK")) {
            action = BuiltInActions.WORLD_GROW_SCULK;
        } else if (type.name().contains("AMETHYST")) {
            action = BuiltInActions.WORLD_GROW_AMETHYST;
        } else if (type.name().contains("MUSHROOM") || type == Material.BAMBOO || type == Material.CHORUS_FLOWER
                || type == Material.CHORUS_PLANT || type == Material.KELP || type == Material.SUGAR_CANE
                || type == Material.CACTUS) {
            action = BuiltInActions.WORLD_GROW_OTHER;
        } else {
            action = BuiltInActions.WORLD_SPREAD;
        }
        if (!guard.allowWorld(action, event.getSource(), target)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        if (!guard.allowWorld(BuiltInActions.WORLD_GROW_CROP, block.getWorld(), block.getX(), block.getY(),
                block.getZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        Block origin = event.getLocation().getBlock();
        Player player = event.getPlayer();
        List<BlockState> blocks = event.getBlocks();
        blocks.removeIf(state -> !guard.allowWorld(BuiltInActions.WORLD_GROW_TREE, origin, state.getBlock())
                || (player != null && !guard.allow(player, BuiltInActions.ITEM_USE_BONE_MEAL,
                        state.getWorld(), state.getX(), state.getY(), state.getZ(), false)));
        if (blocks.isEmpty()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        if (event instanceof EntityBlockFormEvent entityForm) {
            Entity entity = entityForm.getEntity();
            if (entity instanceof Snowman) {
                if (!at(BuiltInActions.WORLD_MOB_SNOW_GOLEM, event.getBlock())) {
                    event.setCancelled(true);
                }
                return;
            }
            Player player = Causes.player(entity);
            if (player != null && !guard.allow(player, BuiltInActions.PLACE, event.getBlock())) {
                event.setCancelled(true);
            }
            return;
        }
        if (!at(BuiltInActions.WORLD_FORM, event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        Material type = event.getBlock().getType();
        ActionId action = type == Material.SNOW || type == Material.ICE || type == Material.SNOW_BLOCK
                || type == Material.FROSTED_ICE ? BuiltInActions.WORLD_MELT
                : Tag.FIRE.isTagged(type) ? BuiltInActions.WORLD_FIRE_BURN : BuiltInActions.WORLD_FADE;
        if (!at(action, event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMoisture(MoistureChangeEvent event) {
        if (!at(BuiltInActions.WORLD_FADE, event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        if (!at(BuiltInActions.WORLD_DECAY, event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        Block source = event.getIgnitingBlock();
        boolean allowed = at(BuiltInActions.WORLD_FIRE_BURN, block)
                && (source == null || guard.reachable(source, block));
        if (!allowed) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() != null || Causes.player(event.getIgnitingEntity()) != null) {
            return;
        }
        Block block = event.getBlock();
        ActionId action = switch (event.getCause()) {
            case LAVA -> BuiltInActions.WORLD_FIRE_LAVA;
            case LIGHTNING -> BuiltInActions.WORLD_FIRE_LIGHTNING;
            case SPREAD -> BuiltInActions.WORLD_FIRE_SPREAD;
            default -> BuiltInActions.WORLD_FIRE_BURN;
        };
        Block source = event.getIgnitingBlock();
        boolean allowed = source == null ? at(action, block) : guard.allowWorld(action, source, block);
        if (!allowed) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!piston(event.getBlock(), event.getBlocks(), event.getDirection(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!piston(event.getBlock(), event.getBlocks(), event.getDirection(), false)) {
            event.setCancelled(true);
        }
    }

    private boolean piston(Block piston, List<Block> moved, BlockFace direction, boolean extending) {
        Block head = piston.getRelative(extending ? direction : direction.getOppositeFace());
        if (!guard.allowWorld(BuiltInActions.WORLD_PISTON, piston, head)) {
            return false;
        }
        for (Block block : moved) {
            Block to = block.getRelative(direction);
            if (!guard.allowWorld(BuiltInActions.WORLD_PISTON, piston, block)
                    || !guard.allowWorld(BuiltInActions.WORLD_PISTON, piston, to)) {
                return false;
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block dispenser = event.getBlock();
        if (dispenser.getBlockData() instanceof Directional directional) {
            Block target = dispenser.getRelative(directional.getFacing());
            if (!guard.allowWorld(BuiltInActions.WORLD_DISPENSER, dispenser, target)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPortal(PortalCreateEvent event) {
        Entity entity = event.getEntity();
        Player player = Causes.player(entity);
        for (BlockState state : event.getBlocks()) {
            boolean allowed = player != null
                    ? guard.allow(player, BuiltInActions.PLACE, state.getWorld(), state.getX(), state.getY(),
                            state.getZ(), true)
                    : guard.allowWorld(BuiltInActions.WORLD_PORTAL, state.getWorld(), state.getX(), state.getY(),
                            state.getZ());
            if (!allowed) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLightning(LightningStrikeEvent event) {
        if (event.getCause() == LightningStrikeEvent.Cause.TRIDENT) {
            return;
        }
        Block block = event.getLightning().getLocation().getBlock();
        if (!at(BuiltInActions.WORLD_LIGHTNING, block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        Block block = event.getBlock();
        if (entity instanceof FallingBlock) {
            return;
        }
        Player player = Causes.player(entity);
        if (player != null) {
            ActionId action = block.getType() == Material.FARMLAND ? BuiltInActions.TRAMPLE_CROP
                    : event.getTo().isAir() ? BuiltInActions.BREAK : BuiltInActions.PLACE;
            if (!guard.allow(player, action, block.getWorld(), block.getX(), block.getY(), block.getZ(), false)) {
                event.setCancelled(true);
            }
            return;
        }
        ActionId action;
        if (entity instanceof Enderman) {
            action = BuiltInActions.WORLD_MOB_ENDERMAN;
        } else if (entity instanceof Ravager) {
            action = BuiltInActions.WORLD_MOB_RAVAGER;
        } else if (entity instanceof Wither) {
            action = BuiltInActions.WORLD_MOB_WITHER;
        } else if (entity instanceof Silverfish) {
            action = BuiltInActions.WORLD_MOB_SILVERFISH;
        } else if (block.getType() == Material.FARMLAND) {
            action = BuiltInActions.TRAMPLE_CROP;
        } else {
            action = BuiltInActions.WORLD_MOB_OTHER;
        }
        if (!at(action, block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDoorBreak(EntityBreakDoorEvent event) {
        if (event.getEntity() instanceof Zombie && !at(BuiltInActions.WORLD_MOB_ZOMBIE_DOOR, event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private boolean at(ActionId action, Block block) {
        return guard.allowWorld(action, block.getWorld(), block.getX(), block.getY(), block.getZ());
    }
}
