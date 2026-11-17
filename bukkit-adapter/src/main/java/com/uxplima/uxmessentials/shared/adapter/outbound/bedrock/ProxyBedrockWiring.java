package com.uxplima.uxmessentials.shared.adapter.outbound.bedrock;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.NetworkConfig;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.FrameSeal;

/**
 * Builds {@link ProxyBedrock} for a backend whose bus can hear the proxy plugin: the network is on and has a usable
 * {@code bus.secret}, without which nothing on the plugin-messaging channel is sent or read.
 */
public final class ProxyBedrockWiring {

    private ProxyBedrockWiring() {}

    /** The relay and the listener that tells it who is here, or empty for a backend that cannot hear the proxy. */
    public static Optional<Wired> wire(Plugin plugin, NetworkConfig network, Bus bus, Scheduler scheduler, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(log, "log");
        if (!network.enabled() || !network.busSecret().usable()) {
            return Optional.empty();
        }
        ProxyBedrock relay = new ProxyBedrock(
                network.serverId(),
                bus.registry().receipts(),
                new FrameSeal(network.busSecret(), System::currentTimeMillis),
                connection(plugin, scheduler, network.channel()),
                log);
        bus.registry().register(relay);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            relay.joined(new PlayerRef(online.getUniqueId(), online.getName()));
        }
        return Optional.of(new Wired(relay, new Presence(relay)));
    }

    /**
     * Each request rides its player's own connection, sent on their thread. The channel is registered by the bus once
     * it starts; a send before that, or after the player left, counts as undelivered.
     */
    private static ProxyBedrock.PlayerConnection connection(Plugin plugin, Scheduler scheduler, String channel) {
        return (UUID player, byte[] sealed, Runnable undelivered) -> scheduler.onEntity(
                new PlayerRef(player, ""),
                () -> {
                    Player here = plugin.getServer().getPlayer(player);
                    if (here == null || !here.isOnline()) {
                        undelivered.run();
                        return;
                    }
                    try {
                        here.sendPluginMessage(plugin, channel, sealed);
                    } catch (RuntimeException refused) {
                        undelivered.run();
                    }
                },
                undelivered);
    }

    /** The relay, and the listener the plugin registers so it knows who is on this backend. */
    public record Wired(ProxyBedrock relay, Listener presence) {

        public Wired {
            Objects.requireNonNull(relay, "relay");
            Objects.requireNonNull(presence, "presence");
        }
    }

    /** Tells the relay who joined and who left, a switch to another backend included. */
    static final class Presence implements Listener {

        private final ProxyBedrock relay;

        Presence(ProxyBedrock relay) {
            this.relay = Objects.requireNonNull(relay, "relay");
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            relay.joined(new PlayerRef(player.getUniqueId(), player.getName()));
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent event) {
            relay.left(event.getPlayer().getUniqueId());
        }
    }
}
