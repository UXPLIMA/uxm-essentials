package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import com.uxplima.uxmessentials.survival.adapter.outbound.PdcSurvivalToggles;
import org.jspecify.annotations.NullMarked;

/**
 * Farmland protection: while a player has farmprotect on, they cannot trample farmland into dirt. Stepping on farmland
 * fires a {@link PlayerInteractEvent} with {@link Action#PHYSICAL} on the farmland block; cancelling it stops the
 * trample. The player's own {@link EntityChangeBlockEvent} (jumping onto farmland from a height) is cancelled too, so
 * neither a step nor a fall breaks the crop bed.
 *
 * <p>Both handlers are per-player: the toggle is read from {@link PdcSurvivalToggles} for the acting player and
 * defaults to on, so a protected player who never touched the command keeps their crops. A mob trampling farmland is
 * not this mechanic's concern — only the player's own {@code EntityChangeBlockEvent} is inspected.
 *
 * <h2>Folia</h2>
 * Both events are dispatched on the region owning the trampled block, and the handler only reads the block type and
 * cancels the event — it mutates nothing — so no scheduler hop is needed.
 */
@NullMarked
public final class FarmProtectListener implements Listener {

    private final PdcSurvivalToggles toggles;

    public FarmProtectListener(PdcSurvivalToggles toggles) {
        this.toggles = Objects.requireNonNull(toggles, "toggles");
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrample(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block != null
                && block.getType() == Material.FARMLAND
                && toggles.farmProtectActive(event.getPlayer(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLandOn(EntityChangeBlockEvent event) {
        if (event.getBlock().getType() != Material.FARMLAND) {
            return;
        }
        if (event.getEntity() instanceof Player player && toggles.farmProtectActive(player, true)) {
            event.setCancelled(true);
        }
    }
}
