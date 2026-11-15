package com.uxplima.uxmessentials.shared.adapter.inbound.hop;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.hop.HopArrival;
import com.uxplima.uxmessentials.shared.application.hop.HopResolvers;

/**
 * The join that ends a cross-server hop: the shared {@link HopArrival} reads the joining player's pending row
 * off-tick and completes or refunds it. While no kind has its cross-server route on, the registry is empty and a
 * join does no hop work at all, not even the read.
 */
public final class HopArrivalListener implements Listener {

    private final HopArrival arrival;
    private final HopResolvers resolvers;

    public HopArrivalListener(HopArrival arrival, HopResolvers resolvers) {
        this.arrival = Objects.requireNonNull(arrival, "arrival");
        this.resolvers = Objects.requireNonNull(resolvers, "resolvers");
    }

    // MONITOR: the join is only observed, never changed.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (resolvers.isEmpty()) {
            return;
        }
        arrival.onArrival(BukkitRefs.toRef(event.getPlayer()));
    }
}
