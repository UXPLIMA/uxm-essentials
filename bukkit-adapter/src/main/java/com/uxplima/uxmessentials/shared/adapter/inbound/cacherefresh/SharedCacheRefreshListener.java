package com.uxplima.uxmessentials.shared.adapter.inbound.cacherefresh;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.SharedCacheRefresh;

/**
 * Tells the {@link SharedCacheRefresh} when a player arrives and leaves, so the first join after an empty spell
 * reloads the caches a peer may have changed meanwhile. The handlers only count players and drop in-memory entries;
 * every database read runs on the async lane.
 */
public final class SharedCacheRefreshListener implements Listener {

    private final SharedCacheRefresh refresh;

    public SharedCacheRefreshListener(SharedCacheRefresh refresh) {
        this.refresh = Objects.requireNonNull(refresh, "refresh");
    }

    // LOWEST so the per-player caches drop before the join handlers warm the joiner, keeping that fresh load.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        refresh.playerJoined();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        refresh.playerLeft();
    }
}
