package com.uxplima.uxmessentials.velocity;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.network.BusChannel;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * The fan-out core of the proxy broker, separated from the Velocity-lifecycle shell so the relay decision is
 * plain Java. It takes a {@link PluginMessageEvent} that already matched the bus channel, decodes the frame
 * header to read its origin server id, then sends the same bytes to every backend <em>except</em> the origin.
 *
 * <p>Two things make the relay safe against replication loops:
 *
 * <ol>
 *   <li>It only relays frames whose source is a {@link ServerConnection}, a backend → proxy message. A
 *       frame the proxy itself sent to a backend arrives with a different source type and is never
 *       re-relayed, so the broker cannot bounce its own output.
 *   <li>It skips the origin backend in the fan-out (origin read from the frame header), and additionally
 *       skips the connection's own server. The receiving backend's bus client also drops any frame whose
 *       origin equals its own id, belt and suspenders, so a mutation reaches each peer exactly once.
 * </ol>
 *
 * <p>A heartbeat ({@code ServerPing}) is also sent back to the backend it came from. The backend drops it as its
 * own frame, but its transport counts it as an answer from the broker, which is how {@code /uxmess doctor} on a
 * backend tells a proxy running this plugin from one without it, where frames leave and nothing relays them.
 *
 * <p>Backends sign each frame with their shared {@code network.bus.secret} and check the signature on arrival. The
 * signature trails the frame, so the header read here is where it always was, and the broker relays the bytes unchanged
 * without holding the secret.
 *
 * <p>A freeze or release it relays is also noted in {@link ProxyFreezes}, which keeps a frozen player on the server
 * that froze them.
 *
 * <p>Every relayed frame is marked {@link PluginMessageEvent.ForwardResult#handled() handled} so the proxy
 * does not also forward it onward through its default plugin-message routing. A frame that fails to decode
 * (a truncated or version-incompatible payload) is dropped with a logged warning rather than relayed blind.
 */
@NullMarked
final class BusBroker {

    private final Supplier<? extends Collection<RegisteredServer>> servers;
    private final ProxyFreezes freezes;
    private final Logger logger;
    private final MinecraftChannelIdentifier channel;

    /**
     * A broker relaying to the backends {@code servers} lists at each frame, the proxy's registered servers, and
     * telling {@code freezes} about each freeze or release it relays.
     */
    BusBroker(Supplier<? extends Collection<RegisteredServer>> servers, ProxyFreezes freezes, Logger logger) {
        this.servers = Objects.requireNonNull(servers, "servers");
        this.freezes = Objects.requireNonNull(freezes, "freezes");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.channel = MinecraftChannelIdentifier.create(BusChannel.NAMESPACE, BusChannel.NAME);
    }

    /** Relay {@code event}'s frame to every backend but its origin, then mark it handled. */
    void relay(PluginMessageEvent event) {
        Objects.requireNonNull(event, "event");
        // Only relay backend → proxy frames; mark every bus frame handled so Velocity does not also route it.
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection sourceConnection)) {
            return;
        }
        String origin = decodeOrigin(event.getData());
        if (origin == null) {
            return;
        }
        String sourceServer = sourceConnection.getServerInfo().getName();
        fanOut(event.getData(), origin, sourceServer);
        freezes.observe(event.getData(), sourceServer);
        if (NetworkMessageCodec.isServerPing(event.getData())) {
            // The heartbeat goes back to its sender as proof that this broker is there: a backend alone behind the
            // proxy has no peer to hear from, and without this answer it could not tell the broker from no broker.
            // Through the server, not the connection: the player may have left or switched servers since the ping,
            // and the server picks whoever is still on it, or sends nothing.
            sourceConnection.getServer().sendPluginMessage(channel, event.getData());
        }
    }

    private void fanOut(byte[] frame, String origin, String sourceServerName) {
        int delivered = 0;
        for (RegisteredServer server : servers.get()) {
            String name = server.getServerInfo().getName();
            // The loop sentinel: never echo a frame back to the backend that produced it. We skip both the
            // declared origin id and the physical source server (in case the two names diverge in config).
            if (name.equals(origin) || name.equals(sourceServerName)) {
                continue;
            }
            if (server.sendPluginMessage(channel, frame)) {
                delivered++;
            }
        }
        logger.debug("relayed bus frame from {} to {} peer(s)", origin, delivered);
    }

    private @org.jspecify.annotations.Nullable String decodeOrigin(byte[] frame) {
        try {
            // The header alone: a frame type this proxy does not know yet is still relayed to the backends.
            return NetworkMessageCodec.readOrigin(frame);
        } catch (IllegalArgumentException malformed) {
            logger.warn("dropping malformed bus frame ({} bytes): {}", frame.length, malformed.getMessage());
            return null;
        }
    }
}
