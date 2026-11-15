package com.uxplima.uxmessentials.shared.adapter.inbound.networkpresence;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.shared.application.networkpresence.TrackNetworkPresence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Tells network presence who joined and left this backend. The tracker only changes memory and queues a bus frame,
 * so the handlers add no I/O to the player's thread.
 */
@NullMarked
public final class NetworkPresenceListener implements Listener {

    private final TrackNetworkPresence track;

    public NetworkPresenceListener(TrackNetworkPresence track) {
        this.track = Objects.requireNonNull(track, "track");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        track.joined(ref(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        track.left(ref(event.getPlayer()));
    }

    private static PlayerRef ref(Player player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }
}
