package com.uxplima.uxmessentials.shared.adapter.inbound.locale;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.message.SharedLocaleStore;

/**
 * Hands a joining player the language an operator set while they were away, and moves a choice kept under this
 * plugin's old key to the one every plugin of ours reads.
 *
 * <p>Read at the lowest priority, on the thread that owns the player, so a welcome another listener writes on
 * join is already in the right language.
 */
public final class SharedLanguageListener implements Listener {

    private final SharedLocaleStore store;

    public SharedLanguageListener(SharedLocaleStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        store.settle(event.getPlayer());
    }
}
