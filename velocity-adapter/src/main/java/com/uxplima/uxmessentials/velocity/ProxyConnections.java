package com.uxplima.uxmessentials.velocity;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.network.FrameSeal;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.uxplima.uxmessentials.shared.network.ProxyConnectionFrame;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import org.jspecify.annotations.NullMarked;

/**
 * Tells each backend a player connects to how they got there: a new login, or a switch on the proxy connection they
 * already had. Backends use it to carry a second-factor proof across a switch and never across a new login.
 *
 * <p>A connection id is made when a player's first backend connection completes and dropped when they disconnect, so
 * it names one proxy connection and is never reused. Each {@link ProxyConnectionFrame} is sealed with the network's
 * {@code bus.secret} and sent over the player's own connection to that backend alone; the backend refuses a frame the
 * secret did not sign, so a client cannot claim a switch.
 */
@NullMarked
final class ProxyConnections {

    private final FrameSeal seal;
    private final ChannelIdentifier channel;
    private final Supplier<UUID> ids;
    private final Map<UUID, Open> open = new ConcurrentHashMap<>();

    ProxyConnections(FrameSeal seal, ChannelIdentifier channel, Supplier<UUID> ids) {
        this.seal = Objects.requireNonNull(seal, "seal");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        player.getCurrentServer()
                .ifPresent(connection -> connection.sendPluginMessage(
                        channel,
                        frame(
                                player.getUniqueId(),
                                player,
                                connection.getServerInfo().getName(),
                                event.getPreviousServer() != null)));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        closed(player.getUniqueId(), player);
    }

    /**
     * The sealed frame for {@code player} reaching {@code server} over the proxy connection {@code session}; a new
     * login opens a new connection id.
     */
    byte[] frame(UUID player, Object session, String server, boolean switched) {
        UUID connection = switched ? current(player, session) : fresh(player, session);
        ProxyConnectionFrame frame =
                new ProxyConnectionFrame(ProxyConnectionFrame.PROXY, ids.get(), player, connection, server, switched);
        return seal.seal(NetworkMessageCodec.encode(frame));
    }

    /**
     * The proxy connection {@code session} of {@code player} ended. Only that connection's id is dropped: the old
     * connection of a player who logged in again may end after the new one began.
     */
    void closed(UUID player, Object session) {
        open.computeIfPresent(player, (id, held) -> held.session() == session ? null : held);
    }

    private UUID current(UUID player, Object session) {
        return open.compute(
                        player,
                        (id, held) -> held != null && held.session() == session ? held : new Open(session, ids.get()))
                .connection();
    }

    private UUID fresh(UUID player, Object session) {
        Open made = new Open(session, ids.get());
        open.put(player, made);
        return made.connection();
    }

    /** One proxy connection: the session object the proxy keeps for it, and the id it is known by. */
    private record Open(Object session, UUID connection) {}
}
