package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import java.util.Objects;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.survival.adapter.outbound.PdcSurvivalToggles;
import com.uxplima.uxmessentials.survival.application.SurvivalConfig.Veinminer;
import com.uxplima.uxmessentials.survival.domain.ConnectedBlockSearch;
import org.jspecify.annotations.NullMarked;

/**
 * Veinminer: breaking one of the configured blocks breaks the whole connected vein of the same material, up to
 * {@code max-blocks}, applying tool durability and hunger. It listens on {@code BlockBreakEvent} (ignoring an
 * already-cancelled break) and fires only for a block in the configured trigger set.
 *
 * <h2>Sneak semantics</h2>
 * The mechanic acts on the breaking player. {@code sneak-required} (default {@code true}) is the "shift to vein"
 * switch: when on, the vein breaks only if the player is sneaking as they mine, so a plain break takes a single ore
 * and a shift-break takes the vein; when off, any break of a configured block veins it. It is read fresh from the
 * config snapshot on every break, so a reload flips the behaviour immediately.
 *
 * <h2>Folia</h2>
 * The connected vein is broken inline on the event thread, which owns the broken block's local region (see
 * {@link SurvivalBlocks}); the search is bounded and grows only through neighbours, so it stays in that region.
 */
@NullMarked
public final class VeinminerListener implements Listener {

    private final Veinminer config;
    private final Set<Material> triggers;
    private final PdcSurvivalToggles toggles;
    private final ConnectedBlockSearch search;

    public VeinminerListener(Veinminer config, Set<Material> triggers, PdcSurvivalToggles toggles) {
        this.config = Objects.requireNonNull(config, "config");
        this.triggers = Set.copyOf(Objects.requireNonNull(triggers, "triggers"));
        this.toggles = Objects.requireNonNull(toggles, "toggles");
        this.search = new ConnectedBlockSearch(config.maxBlocks());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block origin = event.getBlock();
        if (!triggers.contains(origin.getType())) {
            return;
        }
        Player player = event.getPlayer();
        if (applies(player)) {
            vein(origin, player);
        }
    }

    /** Whether veinminer should fire for this break: the player's toggle is on and the sneak gate passes. */
    private boolean applies(Player player) {
        return toggles.veinminerActive(player, true) && (!config.sneakRequired() || player.isSneaking());
    }

    private void vein(Block origin, Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        int broken = SurvivalBlocks.breakConnected(search, origin, tool);
        if (broken == 0) {
            return;
        }
        if (config.toolDurability()) {
            SurvivalBlocks.drainMainHand(player, broken);
        }
        SurvivalBlocks.addExhaustion(player, config.hungerCost(), broken);
    }
}
