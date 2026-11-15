package com.uxplima.uxmessentials.shared.adapter.outbound.action;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.hop.port.HopConnector;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link HopConnector} over the proxy channel every connecting module shares. The hop calls {@link #connect}
 * on the player's entity region, where the online player is looked up and handed to the {@link ServerConnector};
 * a player who left in between is skipped, which leaves the hop to its watchdog.
 */
@NullMarked
public final class BukkitHopConnector implements HopConnector {

    private final ServerConnector connector;

    public BukkitHopConnector(ServerConnector connector) {
        this.connector = Objects.requireNonNull(connector, "connector");
    }

    @Override
    public boolean isAvailable() {
        return connector.isAvailable();
    }

    @Override
    public void connect(PlayerRef player, String server) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(server, "server");
        Player online = Bukkit.getPlayer(player.uuid());
        if (online != null && online.isOnline()) {
            connector.connect(online, server);
        }
    }
}
