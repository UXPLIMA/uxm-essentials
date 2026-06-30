package com.uxplima.uxmessentials.shared.adapter.inbound.playerdata;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.playerdata.CachingPlayerDataStore;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;

/**
 * Warms a joining player's persistent player-data rows into the {@link CachingPlayerDataStore} so the first menu
 * read is a cache hit rather than a cold database read, and drops them on quit so the cache tracks the online set.
 * The load hits the database, so it runs off the join thread through the {@link Scheduler} port's async seam; the
 * evict only clears a cache entry, so it runs inline on the quit event.
 */
public final class PlayerDataLifecycleListener implements Listener {

    private final CachingPlayerDataStore store;
    private final Scheduler scheduler;

    public PlayerDataLifecycleListener(CachingPlayerDataStore store, Scheduler scheduler) {
        this.store = Objects.requireNonNull(store, "store");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        scheduler.async(() -> store.load(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        store.evict(event.getPlayer().getUniqueId());
    }
}
