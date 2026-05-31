package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.network.BusChannel;
import org.jspecify.annotations.NullMarked;

/**
 * The backend's {@code network.conf} view: whether this backend opts into cross-server sync, its unique
 * {@code server-id} (the origin tag stamped into every outbound frame and the loop sentinel on inbound), the
 * bus channel name, and the bounded outbound queue size ({@code docs/09-deployment.md} Path B). These are
 * restart-only — the plugin-messaging channel and the captured server-id are bound once at enable — so a
 * single immutable snapshot is read at wiring time.
 *
 * @param enabled whether this backend participates in network sync; {@code false} runs purely local
 * @param serverId this backend's unique id; two backends sharing it corrupt origin routing
 * @param channel the plugin-messaging channel the proxy broker registers
 * @param outboundQueueSize the cap on buffered outbound frames before the oldest are dropped
 */
@NullMarked
public record NetworkConfig(boolean enabled, String serverId, String channel, int outboundQueueSize) {

    private static final int DEFAULT_QUEUE = 256;
    private static final String DEFAULT_SERVER_ID = "server-1";

    public NetworkConfig {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(channel, "channel");
        if (serverId.isBlank()) {
            throw new IllegalArgumentException("server-id must not be blank");
        }
        if (outboundQueueSize < 1) {
            throw new IllegalArgumentException("outbound-queue-size must be positive: " + outboundQueueSize);
        }
    }

    /**
     * Read the network settings from the {@code network} subtree of {@code config}. The channel defaults to
     * the canonical {@link BusChannel#FULL}; an operator overriding it must match the proxy, or the bridge
     * silences ({@code docs/09-deployment.md}). The bus is disabled by default so a single-server install runs
     * with no proxy and no behavioural change.
     */
    public static NetworkConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        boolean enabled = config.getBoolean("network.enabled", false);
        String serverId = config.getString("network.server-id", DEFAULT_SERVER_ID);
        String channel = config.getString("network.bus-channel", BusChannel.FULL);
        int queue = config.getInt("network.bus.outbound-queue-size", DEFAULT_QUEUE);
        return new NetworkConfig(enabled, serverId.isBlank() ? DEFAULT_SERVER_ID : serverId, channel, queue);
    }
}
