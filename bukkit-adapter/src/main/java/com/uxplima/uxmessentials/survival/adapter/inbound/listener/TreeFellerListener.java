package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.survival.adapter.outbound.PdcSurvivalToggles;
import com.uxplima.uxmessentials.survival.application.SurvivalConfig.TreeFeller;
import com.uxplima.uxmessentials.survival.domain.ConnectedBlockSearch;
import org.jspecify.annotations.NullMarked;

/**
 * Tree-feller: breaking one log of a tree fells every connected log of the same kind, up to {@code max-blocks}, then
 * optionally replants a sapling at the base. It listens on {@code BlockBreakEvent} (ignoring an already-cancelled
 * break) and, when the break is a log and the mechanic applies, breaks the rest of the connected trunk.
 *
 * <h2>Sneak semantics (the owner-shift nuance)</h2>
 * The mechanic acts on the player who broke the log — there is no block ownership involved. The config
 * {@code sneak-required} doubles as the "shift to fell" switch: when {@code true}, tree-feller fires only if the
 * player is sneaking as they break the log, so a normal break chops a single log and a shift-break fells the tree;
 * when {@code false} (the shipped default) any log break fells the tree with no shift needed. It is read fresh from
 * the config snapshot on every break, so a reload flips the behaviour immediately.
 *
 * <h2>Folia</h2>
 * The connected logs are broken inline on the event thread (see {@link SurvivalBlocks}); only the replant is
 * deferred, and it hops to the base block's own region through the injected {@link Scheduler} so it runs a tick later
 * — after the vanilla break has cleared the origin — and never mutates a foreign region from the wrong thread.
 */
@NullMarked
public final class TreeFellerListener implements Listener {

    private final TreeFeller config;
    private final PdcSurvivalToggles toggles;
    private final Scheduler scheduler;
    private final ConnectedBlockSearch search;

    public TreeFellerListener(TreeFeller config, PdcSurvivalToggles toggles, Scheduler scheduler) {
        this.config = Objects.requireNonNull(config, "config");
        this.toggles = Objects.requireNonNull(toggles, "toggles");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.search = new ConnectedBlockSearch(config.maxBlocks());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block origin = event.getBlock();
        if (!Tag.LOGS.isTagged(origin.getType())) {
            return;
        }
        Player player = event.getPlayer();
        if (applies(player)) {
            fell(origin, player);
        }
    }

    /** Whether tree-feller should fire for this break: the player's toggle is on and the sneak/axe gates pass. */
    private boolean applies(Player player) {
        if (!toggles.treeFellerActive(player, true)) {
            return false;
        }
        if (config.sneakRequired() && !player.isSneaking()) {
            return false;
        }
        return !config.requireAxe()
                || SurvivalBlocks.isAxe(
                        player.getInventory().getItemInMainHand().getType());
    }

    private void fell(Block origin, Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        int broken = SurvivalBlocks.breakConnected(search, origin, tool);
        if (broken == 0) {
            return;
        }
        if (config.durabilityDrain()) {
            SurvivalBlocks.drainMainHand(player, broken * config.durabilityMultiplier());
        }
        SurvivalBlocks.addExhaustion(player, config.hungerCost(), broken);
        if (config.replantSaplings()) {
            scheduleReplant(origin);
        }
    }

    /** Replant the matching sapling at the felled base a tick later, once the origin log has been cleared. */
    private void scheduleReplant(Block origin) {
        Optional<Material> sapling = Saplings.forLog(origin.getType());
        if (sapling.isEmpty()
                || !Saplings.isSoil(origin.getRelative(BlockFace.DOWN).getType())) {
            return;
        }
        Material replant = sapling.get();
        World world = origin.getWorld();
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        Position base = BukkitRefs.toPosition(origin.getLocation());
        scheduler.onRegion(base, () -> {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isAir()) {
                block.setType(replant);
            }
        });
    }
}
