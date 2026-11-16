package com.uxplima.uxmessentials.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.network.BusChannel;
import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.uxplima.uxmessentials.shared.network.ServerPing;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.messages.PluginMessageEncoder;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

/** The broker relays a frame to every other backend and returns a heartbeat to the backend that sent it. */
class BusBrokerTest {

    private static final ChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.create(BusChannel.NAMESPACE, BusChannel.NAME);

    private final FakeServer lobby = new FakeServer("lobby-1");
    private final FakeServer survival = new FakeServer("survival-1");
    private final FakeServer creative = new FakeServer("creative-1");
    private final BusBroker broker = new BusBroker(() -> List.of(lobby, survival, creative), NOPLogger.NOP_LOGGER);

    private PluginMessageEvent fromLobby(byte[] frame) {
        FakeConnection source = new FakeConnection(lobby);
        return new PluginMessageEvent(source, source, CHANNEL, frame);
    }

    @Test
    void aHeartbeatReachesEveryOtherBackendAndGoesBackToItsSenderOnce() {
        byte[] ping = NetworkMessageCodec.encode(new ServerPing("lobby-1", 1_700_000_000_000L, 42L));
        PluginMessageEvent event = fromLobby(ping);

        broker.relay(event);

        assertThat(survival.received).containsExactly(ping);
        assertThat(creative.received).containsExactly(ping);
        assertThat(lobby.received).containsExactly(ping);
        assertThat(event.getResult().isAllowed()).isFalse();
    }

    @Test
    void anyOtherFrameReachesTheOtherBackendsAndNeverItsSender() {
        byte[] home = NetworkMessageCodec.encode(new HomeChanged("lobby-1", new UUID(1L, 2L)));

        broker.relay(fromLobby(home));

        assertThat(survival.received).containsExactly(home);
        assertThat(creative.received).containsExactly(home);
        assertThat(lobby.received).isEmpty();
    }

    @Test
    void aHeartbeatToABackendNobodyIsOnAnyMoreIsDroppedQuietly() {
        lobby.empty = true;
        byte[] ping = NetworkMessageCodec.encode(new ServerPing("lobby-1", 1_700_000_000_000L, 42L));

        broker.relay(fromLobby(ping));

        assertThat(lobby.received).isEmpty();
        assertThat(survival.received).containsExactly(ping);
    }

    /** A backend that records what the proxy sends it; with nobody on it, nothing can be sent, as on Velocity. */
    private static final class FakeServer implements RegisteredServer {

        private final ServerInfo info;
        final List<byte[]> received = new ArrayList<>();
        boolean empty;

        FakeServer(String name) {
            this.info = new ServerInfo(name, InetSocketAddress.createUnresolved("localhost", 25565));
        }

        @Override
        public ServerInfo getServerInfo() {
            return info;
        }

        @Override
        public Collection<Player> getPlayersConnected() {
            return List.of();
        }

        @Override
        public CompletableFuture<com.velocitypowered.api.proxy.server.ServerPing> ping() {
            throw new UnsupportedOperationException("the broker never pings");
        }

        @Override
        public CompletableFuture<com.velocitypowered.api.proxy.server.ServerPing> ping(PingOptions options) {
            throw new UnsupportedOperationException("the broker never pings");
        }

        @Override
        public boolean sendPluginMessage(ChannelIdentifier identifier, byte[] data) {
            if (empty) {
                return false;
            }
            received.add(data);
            return true;
        }

        @Override
        public boolean sendPluginMessage(ChannelIdentifier identifier, PluginMessageEncoder encoder) {
            throw new UnsupportedOperationException("the broker sends raw frames");
        }
    }

    /**
     * The connection a frame arrived on. Sending through it throws, as a closed Velocity connection does, so a test
     * fails if the broker answers through the connection instead of the server.
     */
    private record FakeConnection(FakeServer server) implements ServerConnection {

        @Override
        public RegisteredServer getServer() {
            return server;
        }

        @Override
        public Optional<RegisteredServer> getPreviousServer() {
            return Optional.empty();
        }

        @Override
        public ServerInfo getServerInfo() {
            return server.getServerInfo();
        }

        @Override
        public Player getPlayer() {
            throw new UnsupportedOperationException("the broker never asks for the player");
        }

        @Override
        public boolean sendPluginMessage(ChannelIdentifier identifier, byte[] data) {
            throw new IllegalStateException("the connection may already be closed");
        }

        @Override
        public boolean sendPluginMessage(ChannelIdentifier identifier, PluginMessageEncoder encoder) {
            throw new IllegalStateException("the connection may already be closed");
        }
    }
}
