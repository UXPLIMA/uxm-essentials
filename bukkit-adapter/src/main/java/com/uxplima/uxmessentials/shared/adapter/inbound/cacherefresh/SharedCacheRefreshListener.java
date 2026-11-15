package com.uxplima.uxmessentials.shared.adapter.inbound.cacherefresh;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.SharedCacheRefresh;

/**
 * Tells the {@link SharedCacheRefresh} when this backend empties and when a player arrives, so the first join after
 * an empty spell reloads the caches a peer may have changed meanwhile. The handlers only count players; the reload
 * itself runs on the async lane.
 */
public final class SharedCacheRefreshListener implements Listener {

    private final SharedCacheRefresh refresh;
    private final Server server;

    public SharedCacheRefreshListener(SharedCacheRefresh refresh, Server server) {
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.server = Objects.requireNonNull(server, "server");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refresh.playerJoined();
    }

    // The leaving player is still in the online list while the quit event runs, so it is left out of the count.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID leaving = event.getPlayer().getUniqueId();
        int stillOnline = 0;
        for (Player player : server.getOnlinePlayers()) {
            if (!player.getUniqueId().equals(leaving)) {
                stillOnline++;
            }
        }
        refresh.playerLeft(stillOnline);
    }
}
