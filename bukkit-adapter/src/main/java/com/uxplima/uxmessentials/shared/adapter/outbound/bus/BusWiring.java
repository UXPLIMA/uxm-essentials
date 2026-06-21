package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the cross-server bus once from the plugin-wide {@code network.conf}. The bus is a shared-kernel
 * concern, not a feature context — every backend has at most one — so it is wired here in the bootstrap
 * surface alongside the other kernel adapters, and handed to the contexts that opt into sync.
 *
 * <p>The wiring order in {@code PluginModule} is: build the bus (this), wire the feature modules (each
 * registering its remote-sync listener and wrapping its repository with the broadcasting decorator through
 * the returned {@link Bus} handle), then {@link Wired#start()} the client so the channel is registered after
 * every listener is in place. On disable {@link Wired#stop()} unregisters the channel and drops the buffer.
 *
 * <p>When {@code network.conf > enabled} is false the bus is built in a disabled shape: a no-op publisher and
 * an unread registry ({@link Bus#disabled}), and no plugin-messaging channel is registered — the backend runs
 * purely local with no behavioural change, which is the optional-jar contract.
 */
@NullMarked
public final class BusWiring {

    private BusWiring() {}

    /** Build the bus from {@code config}; a disabled backend gets the no-op shape, an enabled one a live client. */
    public static Wired wire(Plugin plugin, ConfigStore config, Scheduler scheduler, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(log, "log");
        NetworkConfig network = NetworkConfig.from(config);
        if (!network.enabled()) {
            return new Wired(Bus.disabled(network.serverId()), null);
        }
        RemoteSyncRegistry registry = new RemoteSyncRegistry();
        BusClient client = new BusClient(plugin, scheduler, log, network, registry);
        return new Wired(new Bus(client, registry), client);
    }

    /**
     * The wired bus: the {@link Bus} handle contexts opt into sync through, plus the lifecycle of the live
     * client (absent for a disabled backend, in which case start/stop are no-ops).
     *
     * @param bus the publish + register seam handed to each context
     * @param client the live bus client, or {@code null} when the bus is disabled
     */
    public record Wired(
            Bus bus, @org.jspecify.annotations.Nullable BusClient client) {

        public Wired {
            Objects.requireNonNull(bus, "bus");
        }

        /** Register the plugin-messaging channel after every context has registered its listener. */
        public void start() {
            if (client != null) {
                client.start();
            }
        }

        /** Unregister the channel and drop buffered frames; a no-op for a disabled backend. */
        public void stop() {
            if (client != null) {
                client.shutdown();
            }
        }
    }
}
