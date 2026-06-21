package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.network.NetworkTransports;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.BusTransport;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the cross-server bus once from the plugin-wide {@code network.conf}. The bus is a shared-kernel
 * concern, not a feature context — every backend has at most one — so it is wired here in the bootstrap
 * surface alongside the other kernel adapters, and handed to the contexts that opt into sync.
 *
 * <p>An enabled backend gets a {@link PluginMessagingTransport} (the byte-moving machinery) wrapped by a
 * {@link BusCore} (the codec + origin stamp + self-origin drop + registry dispatch). The core is both the
 * {@link BusPublisher} the contexts' broadcasting decorators publish through and the lifecycle handle the
 * wiring starts and stops.
 *
 * <p>The wiring order in {@code PluginModule} is: build the bus (this), wire the feature modules (each
 * registering its remote-sync listener and wrapping its repository with the broadcasting decorator through
 * the returned {@link Bus} handle), then {@link Wired#start()} the core so the channel is registered after
 * every listener is in place. On disable {@link Wired#stop()} unregisters the channel and drops the buffer.
 *
 * <p>When {@code network.conf > enabled} is false the bus is built in a disabled shape: a no-op publisher and
 * an unread registry ({@link Bus#disabled}), and no plugin-messaging channel is registered — the backend runs
 * purely local with no behavioural change, which is the optional-jar contract.
 */
@NullMarked
public final class BusWiring {

    private BusWiring() {}

    /** Build the bus from {@code config}; a disabled backend gets the no-op shape, an enabled one a live core. */
    public static Wired wire(Plugin plugin, ConfigStore config, Scheduler scheduler, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(log, "log");
        NetworkConfig network = NetworkConfig.from(config);
        if (!network.enabled()) {
            log.info("network sync disabled (network.conf > enabled=false); bus runs local-only");
            return new Wired(Bus.disabled(network.serverId()), null);
        }
        RemoteSyncRegistry registry = new RemoteSyncRegistry();
        BusTransport transport = selectTransport(plugin, network, scheduler, log);
        BusCore core = new BusCore(transport, network.serverId(), registry, log);
        return new Wired(new Bus(core, registry), new Lifecycle(core, log, network));
    }

    /**
     * Build the transport(s) named by {@code network.transport}: the proxy plugin-messaging carrier, the Redis
     * pub/sub carrier (built through the persistence-adapter factory so the bukkit-adapter never names a Jedis
     * type), or a {@link CompositeBusTransport} fanning over both. An unrecognised value already fell back to
     * {@code velocity} in {@link NetworkConfig}; this logs the WARN so the operator sees their typo without the
     * enable crashing.
     */
    private static BusTransport selectTransport(Plugin plugin, NetworkConfig network, Scheduler scheduler, Logger log) {
        if (!network.transportRecognized()) {
            log.warn(
                    "unknown network.transport value; falling back to {}. valid: velocity | redis | both",
                    network.transport().name().toLowerCase(java.util.Locale.ROOT));
        }
        return switch (network.transport()) {
            case VELOCITY -> pluginMessaging(plugin, network, scheduler);
            case REDIS -> redis(network, scheduler, log);
            case BOTH ->
                new CompositeBusTransport(pluginMessaging(plugin, network, scheduler), redis(network, scheduler, log));
        };
    }

    private static BusTransport pluginMessaging(Plugin plugin, NetworkConfig network, Scheduler scheduler) {
        return new PluginMessagingTransport(plugin, scheduler, network.channel(), network.outboundQueueSize());
    }

    private static BusTransport redis(NetworkConfig network, Scheduler scheduler, Logger log) {
        NetworkConfig.Redis redis = network.redis();
        return NetworkTransports.redis(
                redis.host(), redis.port(), redis.password(), redis.db(), redis.channel(), scheduler, log);
    }

    /**
     * The wired bus: the {@link Bus} handle contexts opt into sync through, plus the lifecycle of the live core
     * (absent for a disabled backend, in which case start/stop are no-ops). It deliberately exposes only the
     * {@link Bus} and the two lifecycle hooks, never the {@link BusCore} or the transport behind them.
     */
    public static final class Wired {

        private final Bus bus;
        private final @org.jspecify.annotations.Nullable Lifecycle lifecycle;

        private Wired(Bus bus, @org.jspecify.annotations.Nullable Lifecycle lifecycle) {
            this.bus = Objects.requireNonNull(bus, "bus");
            this.lifecycle = lifecycle;
        }

        /** The publish + register seam handed to each context. */
        public Bus bus() {
            return bus;
        }

        /** Register the plugin-messaging channel after every context has registered its listener. */
        public void start() {
            if (lifecycle != null) {
                lifecycle.start();
            }
        }

        /** Unregister the channel and drop buffered frames; a no-op for a disabled backend. */
        public void stop() {
            if (lifecycle != null) {
                lifecycle.stop();
            }
        }
    }

    /**
     * The live core plus the diagnostics logged on start. Starting the core registers the plugin-messaging
     * channel through the transport it owns; stopping it unregisters the channel and drops the buffer.
     */
    private record Lifecycle(BusCore core, Logger log, NetworkConfig network) {

        private Lifecycle {
            Objects.requireNonNull(core, "core");
            Objects.requireNonNull(log, "log");
            Objects.requireNonNull(network, "network");
        }

        void start() {
            core.start();
            log.info("network sync enabled on channel {} as server-id {}", network.channel(), network.serverId());
        }

        void stop() {
            core.stop();
        }
    }
}
