package com.uxplima.uxmessentials.guard.adapter.inbound.listener;

import com.uxplima.uxmessentials.guard.adapter.ActionKinds;
import com.uxplima.uxmessentials.guard.adapter.GuardRuntime;
import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.BuiltInActions;
import io.papermc.paper.event.player.PlayerFlowerPotManipulateEvent;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Right clicks on blocks and entities, stepping on blocks, and the special interactions Paper reports on their
 * own (armor stands, lecterns, leads, shears, mounting). A block is classified by {@link ActionKinds#use(Block)};
 * an interactable block the tables do not name is {@code use.other}, never allowed by default.
 */
public final class InteractListener implements Listener {

    private final GuardRuntime guard;

    public InteractListener(GuardRuntime guard) {
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (event.getAction() == Action.PHYSICAL) {
            if (block != null && event.useInteractedBlock() != Event.Result.DENY
                    && !guard.allow(player, physical(block.getType()), block)) {
                event.setUseInteractedBlock(Event.Result.DENY);
                event.setCancelled(true);
            }
            return;
        }
        if (block == null) {
            return;
        }
        ItemStack item = event.getItem();
        Material held = item == null ? Material.AIR : item.getType();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Block face = event.getBlockFace() == null ? block : block.getRelative(event.getBlockFace());
            if (Tag.FIRE.isTagged(face.getType()) && !guard.allow(player, BuiltInActions.BREAK, face)) {
                event.setCancelled(true);
            } else if ((block.getType() == Material.DRAGON_EGG || block.getType() == Material.NOTE_BLOCK)
                    && !guard.allow(player, ActionKinds.use(block), block)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        boolean sneakingWithItem = player.isSneaking() && held != Material.AIR;
        if (!sneakingWithItem && interactable(block) && event.useInteractedBlock() != Event.Result.DENY
                && !guard.allow(player, ActionKinds.use(block), block)) {
            event.setUseInteractedBlock(Event.Result.DENY);
            if (held == Material.AIR || !held.isBlock()) {
                event.setCancelled(true);
            }
            return;
        }
        if (held == Material.AIR) {
            return;
        }
        if (shapesBlocks(held) && !guard.allow(player, BuiltInActions.PLACE, block)) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
            return;
        }
        if ((held.name().endsWith("_SPAWN_EGG") || held == Material.FIREWORK_ROCKET)
                && !guard.allowItem(player, ActionKinds.item(held), block.getLocation(), held.name())) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity target = event.getRightClicked();
        if (!guard.allow(event.getPlayer(), ActionKinds.interact(target), target.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (!guard.allow(event.getPlayer(), BuiltInActions.INTERACT_ARMOR_STAND, event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLectern(PlayerTakeLecternBookEvent event) {
        if (!guard.allow(event.getPlayer(), BuiltInActions.USE_LECTERN, event.getLectern().getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlowerPot(PlayerFlowerPotManipulateEvent event) {
        if (!guard.allow(event.getPlayer(), BuiltInActions.USE_OTHER, event.getFlowerpot())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (!guard.allow(event.getPlayer(), BuiltInActions.INTERACT_ANIMAL, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        if (!guard.allow(event.getPlayer(), BuiltInActions.INTERACT_ANIMAL, event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player
                && !guard.allow(player, BuiltInActions.INTERACT_VEHICLE, event.getVehicle().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player && !(event.getMount() instanceof org.bukkit.entity.Vehicle)
                && !guard.allow(player, ActionKinds.interact(event.getMount()), event.getMount().getLocation())) {
            event.setCancelled(true);
        }
    }

    // Paper deprecated isInteractable because a few blocks report it without reacting to every click; that is
    // the safe side for protection (a click on them is checked, never let through), and nothing else tells a
    // stone block from a lever without a table of our own going stale with every Minecraft version.
    @SuppressWarnings("deprecation")
    private static boolean interactable(Block block) {
        org.bukkit.block.BlockType type = block.getType().asBlockType();
        return type != null && type.isInteractable();
    }

    private static ActionId physical(Material type) {
        if (type == Material.FARMLAND) {
            return BuiltInActions.TRAMPLE_CROP;
        }
        if (type == Material.TURTLE_EGG || type == Material.SNIFFER_EGG) {
            return BuiltInActions.TRAMPLE_TURTLE_EGG;
        }
        if (type == Material.BIG_DRIPLEAF) {
            return BuiltInActions.TRAMPLE_DRIPLEAF;
        }
        if (Tag.PRESSURE_PLATES.isTagged(type)) {
            return BuiltInActions.USE_PRESSURE_PLATE;
        }
        if (type == Material.TRIPWIRE || type == Material.REDSTONE_ORE || type == Material.DEEPSLATE_REDSTONE_ORE) {
            return BuiltInActions.USE_REDSTONE;
        }
        return BuiltInActions.USE_OTHER;
    }

    /** Tools that reshape the clicked block: stripping, pathing, tilling, waxing, scraping, brushing. */
    private static boolean shapesBlocks(Material held) {
        return Tag.ITEMS_AXES.isTagged(held) || Tag.ITEMS_SHOVELS.isTagged(held) || Tag.ITEMS_HOES.isTagged(held)
                || held == Material.HONEYCOMB || held == Material.BRUSH || held == Material.GLASS_BOTTLE
                || held == Material.SHEARS;
    }
}
