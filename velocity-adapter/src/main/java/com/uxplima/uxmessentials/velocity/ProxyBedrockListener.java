package com.uxplima.uxmessentials.velocity;

import java.util.Objects;
import java.util.UUID;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import org.jspecify.annotations.NullMarked;

/**
 * Feeds {@link ProxyBedrockForms} from the proxy's events: a player reaching a backend, switching away from one, and
 * leaving. It also carries what the forms say to a backend, over the player's own connection to it.
 */
@NullMarked
final class ProxyBedrockListener {

    private final ProxyBedrockForms forms;

    ProxyBedrockListener(ProxyBedrockForms forms) {
        this.forms = Objects.requireNonNull(forms, "forms");
    }

    /** The backend connections of {@code proxy}'s players, on {@code channel}. */
    static ProxyBedrockForms.Connections connections(ProxyServer proxy, ChannelIdentifier channel) {
        return (UUID player, String server, byte[] sealed) -> proxy.getPlayer(player)
                .flatMap(Player::getCurrentServer)
                .filter(connection -> connection.getServerInfo().getName().equals(server))
                .ifPresent(connection -> connection.sendPluginMessage(channel, sealed));
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        // A switch: forms shown for the backend they leave no longer answer there.
        forms.left(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        player.getCurrentServer()
                .ifPresent(connection -> forms.connected(
                        player.getUniqueId(), connection.getServerInfo().getName()));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        forms.left(event.getPlayer().getUniqueId());
    }
}
