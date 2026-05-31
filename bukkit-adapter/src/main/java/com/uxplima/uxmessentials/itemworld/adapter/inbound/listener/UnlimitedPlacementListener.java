package com.uxplima.uxmessentials.itemworld.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.itemworld.adapter.outbound.UnlimitedPlacementStore;
import com.uxplima.uxmessentials.itemworld.application.ItemworldConfig;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Tops a placed block stack back up for a player who has {@code /unlimited} on, so it never empties. Gated, in
 * order, on the mob/entity sub-feature group + {@code unlimited} per-command disable (live
 * {@link ItemworldConfig} view) and the player's per-player flag ({@link UnlimitedPlacementStore}); a player
 * without unlimited on is untouched, so an ordinary place stays a no-op on this listener.
 *
 * <p>The refill restores the placed item's amount on the player's own region thread (the event fires there), so
 * it is valid on Folia. Quit forgets the flag so a relog starts off.
 */
@NullMarked
public final class UnlimitedPlacementListener implements Listener {

    private final UnlimitedPlacementStore store;
    private final ItemworldConfig config;

    public UnlimitedPlacementListener(UnlimitedPlacementStore store, ItemworldConfig config) {
        this.store = Objects.requireNonNull(store, "store");
        this.config = Objects.requireNonNull(config, "config");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!config.commandEnabled(SubFeatureGroup.MOB_ENTITY, "unlimited")) {
            return;
        }
        Player player = event.getPlayer();
        PlayerRef who = BukkitRefs.toRef(player);
        if (!store.enabled(who)) {
            return;
        }
        ItemStack inHand = event.getItemInHand();
        if (inHand.getType().isAir()) {
            return;
        }
        ItemStack refill = inHand.clone();
        refill.setAmount(inHand.getMaxStackSize());
        player.getInventory().setItem(event.getHand(), refill);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        store.forget(BukkitRefs.toRef(event.getPlayer()));
    }
}
