package com.uxplima.uxmessentials.itemworld.adapter.inbound.listener;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.itemworld.adapter.outbound.PdcPowertoolStore;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.PowertoolToggleStore;
import com.uxplima.uxmessentials.itemworld.application.ItemworldConfig;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Fires a held item's powertool binding when the player uses it. On a left- or right-click with a bound item
 * the stored command lines (read from item PDC by {@link PdcPowertoolStore}) are run as the player, and the
 * interaction is cancelled so the bound action does not also place/break/use the item.
 *
 * <p>Three gates run before any dispatch, cheapest first, so an ordinary interact stays hot-path-light: the
 * powertool sub-feature group must be enabled and the {@code powertool} command not per-command disabled
 * (live {@link ItemworldConfig} view); the player's {@code /powertooltoggle} switch must be on
 * ({@link PowertoolToggleStore}); and the held item must actually carry a binding. The event fires on the
 * player's own region thread, so dispatching the command there is valid on Folia. Quit forgets the toggle so a
 * relog starts from the default.
 */
@NullMarked
public final class PowertoolInteractListener implements Listener {

    private final PdcPowertoolStore bindings;
    private final PowertoolToggleStore toggles;
    private final ItemworldConfig config;

    public PowertoolInteractListener(PdcPowertoolStore bindings, PowertoolToggleStore toggles, ItemworldConfig config) {
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.toggles = Objects.requireNonNull(toggles, "toggles");
        this.config = Objects.requireNonNull(config, "config");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!isUse(event.getAction()) || event.getHand() == null) {
            return;
        }
        if (!config.commandEnabled(SubFeatureGroup.POWERTOOL, "powertool")) {
            return;
        }
        Player player = event.getPlayer();
        PlayerRef who = BukkitRefs.toRef(player);
        if (!toggles.enabled(who)) {
            return;
        }
        ItemStack hand = event.getItem();
        if (hand == null || hand.getType().isAir()) {
            return;
        }
        Optional<List<String>> commands = bindings.commandsOf(hand);
        if (commands.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        commands.get().forEach(line -> player.performCommand(line));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        toggles.forget(BukkitRefs.toRef(event.getPlayer()));
    }

    private static boolean isUse(Action action) {
        return action == Action.LEFT_CLICK_AIR
                || action == Action.LEFT_CLICK_BLOCK
                || action == Action.RIGHT_CLICK_AIR
                || action == Action.RIGHT_CLICK_BLOCK;
    }
}
