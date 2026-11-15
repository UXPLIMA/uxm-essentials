package com.uxplima.uxmessentials.bootstrap.di;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.hop.HopStores;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.BukkitHopConnector;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.BukkitServerConnector;
import com.uxplima.uxmessentials.shared.adapter.outbound.hop.CrossServerHops;
import com.uxplima.uxmessentials.shared.application.hop.HopArrival;
import com.uxplima.uxmessentials.shared.application.hop.HopCharges;
import com.uxplima.uxmessentials.shared.application.hop.HopResolvers;
import com.uxplima.uxmessentials.shared.application.hop.HopWatchdog;
import com.uxplima.uxmessentials.shared.application.hop.port.HopConnector;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHopStore;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * Builds the {@link CrossServerHops} every teleport kind shares, once per backend before the modules wire, over
 * the network-shared pending-hop store and the proxy channel. The timings are read from the {@code cross-server}
 * block of {@code config.conf}, once, on enable.
 */
final class HopWiring {

    static final int SHIPPED_ARRIVAL_DELAY_TICKS = 20;
    static final int SHIPPED_REQUEST_TTL_SECONDS = 30;
    private static final String LEGACY_ROOT = "modules.playerwarps.cross-server.";
    private static final int UNSET = Integer.MIN_VALUE;

    private HopWiring() {}

    static CrossServerHops wire(
            Plugin plugin, ConfigStore config, KernelPorts kernel, Persistence persistence, String server) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(server, "server");
        Settings settings = Settings.read(config, kernel.log());
        Clock clock = Clock.systemUTC();
        PendingHopStore store = HopStores.pending(persistence);
        HopResolvers resolvers = new HopResolvers();
        HopCharges charges = new HopCharges(new Notifier(kernel.messages(), kernel.messageSink()), kernel.log());
        HopWatchdog watchdog = new HopWatchdog(
                store, resolvers, charges, kernel.scheduler(), kernel.log(), settings.requestTtl(), clock);
        HopArrival arrival = new HopArrival(
                store,
                resolvers,
                charges,
                kernel.scheduler(),
                kernel.log(),
                server,
                settings.arrivalDelay(),
                settings.requestTtl(),
                clock);
        HopConnector connector = new BukkitHopConnector(new BukkitServerConnector(plugin, kernel.log()));
        return new CrossServerHops(resolvers, charges, watchdog, arrival, connector, kernel.scheduler(), server, clock);
    }

    /**
     * The hop timings. A file from before the block existed has them under the player-warps module, and an update
     * appends the block here with the shipped values, so the old keys answer while the block still holds those.
     */
    record Settings(Duration arrivalDelay, Duration requestTtl) {

        static Settings read(ConfigStore config, Logger log) {
            int ticks = readOrLegacy(config, log, "arrival-delay-ticks", SHIPPED_ARRIVAL_DELAY_TICKS);
            long ttl = readOrLegacy(config, log, "request-ttl-seconds", SHIPPED_REQUEST_TTL_SECONDS);
            return new Settings(Duration.ofMillis(Math.max(0, ticks) * 50L), Duration.ofSeconds(Math.max(1L, ttl)));
        }

        private static int readOrLegacy(ConfigStore config, Logger log, String key, int shipped) {
            int written = config.getInt("cross-server." + key, shipped);
            int legacy = config.getInt(LEGACY_ROOT + key, UNSET);
            if (legacy == UNSET) {
                return written;
            }
            if (written != shipped && written != legacy) {
                log.warn(
                        "config.conf sets cross-server.{} = {}, which is read; the old {}{} = {} in"
                                + " modules/playerwarps/config.conf is not. Remove the old line.",
                        key,
                        written,
                        LEGACY_ROOT,
                        key,
                        legacy);
                return written;
            }
            return written == shipped ? legacy : written;
        }
    }
}
