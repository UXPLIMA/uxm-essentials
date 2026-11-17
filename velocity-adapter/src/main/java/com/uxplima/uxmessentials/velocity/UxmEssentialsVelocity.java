package com.uxplima.uxmessentials.velocity;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

import com.google.inject.Inject;
import com.uxplima.uxmessentials.shared.network.BusChannel;
import com.uxplima.uxmessentials.shared.network.BusSecret;
import com.uxplima.uxmessentials.shared.network.FrameSeal;
import com.uxplima.uxmessentials.velocity.commandcontrol.CommandControlConfigLoader;
import com.uxplima.uxmessentials.velocity.commandcontrol.ProxyCatalog;
import com.uxplima.uxmessentials.velocity.commandcontrol.ProxyCommandControl;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;

/**
 * The proxy-side cross-server bus broker, the entry point of the optional {@code uxmessentials-velocity}
 * jar. It registers the {@link BusChannel bus channel} on the proxy and relays uxmEssentials sync frames
 * from one backend to the others, so a {@code /sethome} on one backend invalidates the home cache on every
 * other backend in the cluster.
 *
 * <p>This class is a thin Velocity-lifecycle shell: it holds the injected {@link ProxyServer} and
 * {@link Logger}, registers the channel and the {@link FrozenServerSwitch} on proxy init, and forwards the
 * plugin-message event to the {@link BusBroker}, which owns the fan-out logic (and is plain Java, testable without a
 * live proxy). The broker notes each freeze it relays in the {@link ProxyFreezes} the switch guard reads, so a frozen
 * player stays on the server that froze them. Construction is by constructor injection.
 */
@Plugin(
        id = "uxmessentials-velocity",
        name = "uxmEssentials Velocity",
        version = BuildConstants.VERSION,
        description = "Cross-server bus broker and proxy command-control for uxmEssentials.",
        authors = {"UXPLIMA"},
        dependencies = {@Dependency(id = "luckperms", optional = true)})
@NullMarked
public final class UxmEssentialsVelocity {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final ProxyFreezes freezes = new ProxyFreezes(System::currentTimeMillis);
    private final BusBroker broker;
    private final MinecraftChannelIdentifier channel;

    @Inject
    public UxmEssentialsVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.broker = new BusBroker(proxy::getAllServers, freezes, logger);
        this.channel = MinecraftChannelIdentifier.create(BusChannel.NAMESPACE, BusChannel.NAME);
    }

    /** Register the bus channel and, when configured, the proxy command-control layer on proxy init. */
    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(channel);
        logger.info("uxmEssentials bus broker ready on channel {}", channel.getId());
        proxy.getEventManager()
                .register(
                        this,
                        new FrozenServerSwitch(
                                freezes, proxy::getServer, ProxyCatalog.load(dataDirectory, logger::warn)));
        ProxyCommandControl.enable(this, proxy, logger, dataDirectory);
        announceConnections();
    }

    /**
     * Tell backends how each player reached them, when {@code bus.secret} lets the proxy seal what it says. Without a
     * usable secret nothing is sent, and a backend asks for the second factor again on a switch.
     */
    private void announceConnections() {
        BusSecret secret;
        try {
            secret = BusSecret.of(CommandControlConfigLoader.load(dataDirectory)
                    .node("bus", "secret")
                    .getString(""));
        } catch (ConfigurateException | RuntimeException unreadable) {
            logger.warn(
                    "could not read bus.secret from config.conf; switches are not announced: {}",
                    unreadable.getMessage());
            return;
        }
        if (!secret.usable()) {
            logger.info("bus.secret in config.conf is not set (or too weak), so backends are not told about server"
                    + " switches and a switch asks for the second factor again");
            return;
        }
        proxy.getEventManager()
                .register(
                        this,
                        new ProxyConnections(
                                new FrameSeal(secret, System::currentTimeMillis), channel, UUID::randomUUID));
    }

    /** Relay an incoming bus frame from its origin backend to every other backend. */
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!channel.getId().equals(event.getIdentifier().getId())) {
            return;
        }
        broker.relay(event);
    }
}
