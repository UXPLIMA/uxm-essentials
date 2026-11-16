package com.uxplima.uxmessentials.shared.adapter.inbound.lookup;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.lookup.OnlinePlayerIndex;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps the {@link OnlinePlayerIndex} in step with the server. A join is recorded first and a quit last, so every
 * other handler of either event already sees the player as online, as it would asking the server. The live
 * {@code Player} names the connection, a new object for every login, so a late quit of an old one is told apart.
 */
@NullMarked
public final class OnlinePlayerIndexListener implements Listener {

    private final OnlinePlayerIndex index;

    public OnlinePlayerIndexListener(OnlinePlayerIndex index) {
        this.index = Objects.requireNonNull(index, "index");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        index.joined(BukkitRefs.toRef(event.getPlayer()), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        index.left(event.getPlayer().getUniqueId(), event.getPlayer());
    }
}
