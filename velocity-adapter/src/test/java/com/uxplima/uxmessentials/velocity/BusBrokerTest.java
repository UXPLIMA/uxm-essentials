package com.uxplima.uxmessentials.velocity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.ChannelMessageSource;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class BusBrokerTest {

    private static final byte[] FRAME = NetworkMessageCodec.encode(new HomeChanged("survival-1", UUID.randomUUID()));
    private static final InetSocketAddress ADDRESS = new InetSocketAddress(InetAddress.getLoopbackAddress(), 25565);

    @Test
    void fansOutToEveryBackendButTheOrigin() {
        RegisteredServer origin = server("survival-1");
        RegisteredServer peerA = server("lobby-1");
        RegisteredServer peerB = server("survival-2");
        ProxyServer proxy = mock(ProxyServer.class);
        when(proxy.getAllServers()).thenReturn(List.of(origin, peerA, peerB));
        BusBroker broker = new BusBroker(proxy, mock(Logger.class));

        broker.relay(event(FRAME, sourceConnection("survival-1")));

        verify(origin, never()).sendPluginMessage(any(ChannelIdentifier.class), any(byte[].class));
        verify(peerA).sendPluginMessage(any(ChannelIdentifier.class), eq(FRAME));
        verify(peerB).sendPluginMessage(any(ChannelIdentifier.class), eq(FRAME));
    }

    @Test
    void marksEveryBusFrameHandledSoVelocityDoesNotAlsoRouteIt() {
        ProxyServer proxy = mock(ProxyServer.class);
        when(proxy.getAllServers()).thenReturn(List.of());
        BusBroker broker = new BusBroker(proxy, mock(Logger.class));
        PluginMessageEvent event = event(FRAME, sourceConnection("survival-1"));

        broker.relay(event);

        verify(event).setResult(PluginMessageEvent.ForwardResult.handled());
    }

    @Test
    void doesNotRelayAFrameWhoseSourceIsNotABackendConnection() {
        RegisteredServer peer = server("lobby-1");
        ProxyServer proxy = mock(ProxyServer.class);
        BusBroker broker = new BusBroker(proxy, mock(Logger.class));
        // A non-ServerConnection source is a proxy → backend frame; relaying it would loop.
        PluginMessageEvent event = event(FRAME, mock(ChannelMessageSource.class));

        broker.relay(event);

        verify(proxy, never()).getAllServers();
        verify(peer, never()).sendPluginMessage(any(ChannelIdentifier.class), any(byte[].class));
    }

    @Test
    void dropsAMalformedFrameWithoutRelaying() {
        ProxyServer proxy = mock(ProxyServer.class);
        BusBroker broker = new BusBroker(proxy, mock(Logger.class));

        broker.relay(event(new byte[] {9, 9, 9}, sourceConnection("survival-1")));

        verify(proxy, never()).getAllServers();
    }

    private static PluginMessageEvent event(byte[] data, ChannelMessageSource source) {
        PluginMessageEvent event = mock(PluginMessageEvent.class);
        when(event.getData()).thenReturn(data);
        when(event.getSource()).thenReturn(source);
        return event;
    }

    private static ServerConnection sourceConnection(String serverName) {
        ServerConnection connection = mock(ServerConnection.class);
        when(connection.getServerInfo()).thenReturn(new ServerInfo(serverName, ADDRESS));
        return connection;
    }

    private static RegisteredServer server(String name) {
        RegisteredServer server = mock(RegisteredServer.class);
        when(server.getServerInfo()).thenReturn(new ServerInfo(name, ADDRESS));
        return server;
    }
}
