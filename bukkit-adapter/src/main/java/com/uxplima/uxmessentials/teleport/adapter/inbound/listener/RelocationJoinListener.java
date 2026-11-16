package com.uxplima.uxmessentials.teleport.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.teleport.application.OfflineTeleport;

/**
 * Hands a player who has joined to {@link OfflineTeleport#arrive}, which applies a {@code /tpofflinehere} move left
 * for them on this server. Joined means placed and spawned, so the server has finished with their saved data before
 * the move; the lookup itself runs off the tick.
 */
public final class RelocationJoinListener implements Listener {

    private final OfflineTeleport offlineTeleport;

    public RelocationJoinListener(OfflineTeleport offlineTeleport) {
        this.offlineTeleport = Objects.requireNonNull(offlineTeleport, "offlineTeleport");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        offlineTeleport.arrive(BukkitRefs.toRef(event.getPlayer()));
    }
}
