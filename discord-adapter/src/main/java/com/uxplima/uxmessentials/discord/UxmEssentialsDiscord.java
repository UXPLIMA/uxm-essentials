package com.uxplima.uxmessentials.discord;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.uxplima.uxmessentials.api.audit.AuditFeed;
import com.uxplima.uxmessentials.api.link.DiscordLinkConfirmation;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;

/**
 * The entry point of the optional {@code uxmessentials-discord} jar. Its own Paper plugin, declared by this
 * module's {@code paper-plugin.yml} and soft-depending on the host {@code uxmEssentials} (docs/09-deployment.md
 * Path C). It is a thin lifecycle shell: load {@code discord.conf} on enable, connect off-tick only when a
 * token is configured, and shut JDA down cleanly on disable. With no token the plugin stays dormant and the
 * host plugin runs entirely unaffected.
 *
 * <h2>Host integration</h2>
 * The bridge reads the host's {@link AuditFeed} through Bukkit's {@code ServicesManager}: there is no compile-time
 * link to {@code :bukkit-adapter}. The subscription that feeds the {@link NotificationForwarder} opens once the gateway
 * is ready; until then the forwarder simply drops everything (an unconnected gateway forwards nothing).
 *
 * <p>Every service the two jars share is an {@code :api} class, and this jar carries no copy of {@code :api}: the
 * manifest joins the host's classpath, so the class the bridge asks for is the one the host registered. With the host
 * absent those classes do not exist here, so nothing that names one runs until the host is known to be enabled.
 *
 * <h2>Threading</h2>
 * The JDA login blocks, so it runs on Paper's async scheduler ({@code getAsyncScheduler().runNow}), never on
 * the enable (main) thread. The live forwarder is published into an {@link AtomicReference} so a disable that
 * races the async connect either sees the whole forwarder or none.
 */
public final class UxmEssentialsDiscord extends JavaPlugin {

    /** The host's name in its own manifest, the plugin this bridge mirrors. */
    private static final String HOST = "uxmEssentials";

    private final DiscordGateway gateway;
    private final AtomicReference<NotificationForwarder> forwarder = new AtomicReference<>();
    private final AtomicReference<AuditNoticeSubscriber> subscriber = new AtomicReference<>();
    private final AtomicReference<BridgePresence> presence = new AtomicReference<>();

    /** Production constructor: a real JDA gateway. */
    public UxmEssentialsDiscord() {
        this(new JdaDiscordGateway());
    }

    /** Test/seam constructor: inject a fake gateway so enable wiring is exercised without a live JDA login. */
    UxmEssentialsDiscord(DiscordGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    @Override
    public void onEnable() {
        DiscordConfig config = loadConfig();
        if (config == null || !config.shouldConnect()) {
            getLogger().info("discord bridge dormant. No token configured (edit config/discord.conf)");
            return;
        }
        Bukkit.getAsyncScheduler().runNow(this, ignored -> connect(config));
    }

    @Override
    public void onDisable() {
        BridgePresence advertised = presence.getAndSet(null);
        if (advertised != null) {
            advertised.withdraw();
        }
        AuditNoticeSubscriber open = subscriber.getAndSet(null);
        if (open != null) {
            open.stop();
        }
        forwarder.set(null);
        gateway.shutdown();
    }

    private @Nullable DiscordConfig loadConfig() {
        try {
            return DiscordConfigLoader.load(getDataFolder().toPath());
        } catch (ConfigurateException | DiscordConfigLoader.UncheckedExtractException failure) {
            getLogger().warning("discord bridge disabled, could not load config: " + failure.getMessage());
            return null;
        }
    }

    private void connect(DiscordConfig config) {
        try {
            gateway.connect(config.token());
            NotificationForwarder live = new NotificationForwarder(gateway, config);
            forwarder.set(live);
            if (!getServer().getPluginManager().isPluginEnabled(HOST)) {
                getLogger()
                        .warning("discord bridge connected but uxmEssentials is not enabled on this server: nothing "
                                + "will be forwarded and /link is dormant");
                return;
            }
            startSubscription(config, live);
            enableLinking();
            getLogger()
                    .info("discord bridge connected; mirroring "
                            + config.channels().size() + " channel(s)");
        } catch (DiscordConnectException failure) {
            getLogger().warning("discord bridge self-disabled. Connection failed: " + failure.getMessage());
        }
    }

    /**
     * Find the host's {@link AuditFeed} via {@code ServicesManager} (no compile-time link to the host jar) and start
     * the subscriber. When the host exposes no feed, because it is older than the bridge, the bridge stays connected
     * but forwards nothing, logging why rather than failing.
     */
    private void startSubscription(DiscordConfig config, NotificationForwarder live) {
        NotificationSource source = lookupSource();
        if (source == null) {
            getLogger()
                    .warning("discord bridge connected but uxmEssentials publishes no audit feed, nothing will be "
                            + "forwarded (update uxmEssentials to the same version as this bridge)");
            return;
        }
        AuditNoticeSubscriber active = new AuditNoticeSubscriber(source, live, rateLimiter(config));
        active.start();
        subscriber.set(active);
    }

    private @Nullable NotificationSource lookupSource() {
        RegisteredServiceProvider<AuditFeed> rsp =
                getServer().getServicesManager().getRegistration(AuditFeed.class);
        return rsp != null ? new HostAuditSource(rsp.getProvider()) : null;
    }

    /**
     * Register the {@code /link} slash command behind the host's confirmation seam, looked up via {@code
     * ServicesManager} (no compile-time link to the host jar). When the host exposes no confirmation: it is
     * older than the bridge, or the discordlink module is disabled. Linking stays dormant: the bridge runs the
     * outbound notice mirror unchanged and logs why, exactly like the notification-source dormant path.
     *
     * <p>Once linking is live, advertise the bridge's presence back to the host so it knows a {@code /discordlink}
     * code now has somewhere to be redeemed. Presence is published only here, after a real connect and a found
     * confirmation seam, so the host's "Discord is configured" signal means exactly "connected and redeemable".
     */
    private void enableLinking() {
        DiscordLinkConfirmation confirmation = lookupConfirmation();
        if (confirmation == null) {
            getLogger()
                    .info("discord bridge connected but the host exposes no link confirmation: /link is "
                            + "dormant (is uxmEssentials installed with the discordlink module enabled?)");
            return;
        }
        gateway.enableLinking(confirmation);
        BridgePresence advertised = new BridgePresence(getServer().getServicesManager(), this);
        advertised.publish();
        presence.set(advertised);
    }

    private @Nullable DiscordLinkConfirmation lookupConfirmation() {
        RegisteredServiceProvider<DiscordLinkConfirmation> rsp =
                getServer().getServicesManager().getRegistration(DiscordLinkConfirmation.class);
        return rsp != null ? rsp.getProvider() : null;
    }

    private static NotificationRateLimiter rateLimiter(DiscordConfig config) {
        LongSupplier clock = System::currentTimeMillis;
        return new NotificationRateLimiter(config.maxPerMinute(), Duration.ofMinutes(1), clock);
    }

    /** The live forwarder once connected, or {@code null} while dormant: the seam host wiring publishes to. */
    @Nullable NotificationForwarder forwarder() {
        return forwarder.get();
    }

    /** The live subscriber once connected to a host source, or {@code null} while dormant, test seam. */
    @Nullable AuditNoticeSubscriber subscriber() {
        return subscriber.get();
    }
}
