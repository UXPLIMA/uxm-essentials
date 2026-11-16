package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.security.SecureRandom;
import java.util.Objects;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.BusTransport;
import com.uxplima.uxmessentials.shared.network.ServerPing;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the cross-server bus once from the {@code network} block of {@code config.conf}. The bus is a shared-kernel
 * concern, not a feature context, every backend has at most one, so it is wired here in the bootstrap
 * surface alongside the other kernel adapters, and handed to the contexts that opt into sync.
 *
 * <p>An enabled backend gets a {@link PluginMessagingTransport} (the byte-moving machinery) wrapped by a
 * {@link BusCore} (the codec + origin stamp + self-origin drop + registry dispatch). The core is both the
 * {@link BusPublisher} the contexts' broadcasting decorators publish through and the lifecycle handle the
 * wiring starts and stops. An enabled backend also gets a {@link ClusterPeers} roster and a
 * {@link ClusterHeartbeat}: the heartbeat publishes a presence ping on a bounded interval, peers record each
 * other's pings into their roster, and {@code /uxmess doctor} reads the live peer count from it.
 *
 * <p>The wiring order in {@code PluginModule} is: build the bus (this), wire the feature modules (each
 * registering its remote-sync listener and wrapping its repository with the broadcasting decorator through
 * the returned {@link Bus} handle), then {@link Wired#start()} the core so the channel is registered after
 * every listener is in place. On disable {@link Wired#stop()} unregisters the channel and drops the buffer.
 *
 * <p>When {@code network.enabled} is false the bus is built in a disabled shape: a no-op publisher and
 * an unread registry ({@link Bus#disabled}), and no plugin-messaging channel is registered, the backend runs
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
            log.info("network sync disabled (network.enabled = false in config.conf); bus runs local-only");
            return new Wired(Bus.disabled(network.serverId()), null, null, null, network, Carriers.NONE, scheduler);
        }
        if (NetworkConfig.isDefaultServerName(network.serverId())) {
            log.warn(
                    "network.server-name in config.conf is still the default '{}' while network.enabled is true."
                            + " Give every backend its own name (for example server-name = \"survival-1\"): two"
                            + " backends left on the same name drop each other's sync frames.",
                    network.serverId());
        }
        RemoteSyncRegistry registry = new RemoteSyncRegistry();
        Carriers carriers = selectTransport(plugin, network, scheduler, log);
        BusTransport transport = carriers.transport();
        long bootNonce = bootNonce();
        DuplicateServerNames duplicates = new DuplicateServerNames(
                network.serverId(), bootNonce, network.peerLivenessWindow(), System::currentTimeMillis, log);
        BusCore core = new BusCore(transport, network.serverId(), registry, duplicates, log);
        // The cluster roster and the presence heartbeat are kernel concerns (one per backend), so they are wired
        // here next to the core rather than in any feature context. The roster's listener records inbound pings;
        // the heartbeat publishes this backend's own ping on the bounded interval, both bound to the live
        // lifecycle below: a disabled backend builds neither.
        ClusterPeers peers = new ClusterPeers(network.peerLivenessWindow());
        registry.register(peers.listener());
        ClusterHeartbeat heartbeat = new ClusterHeartbeat(core, scheduler, network.heartbeatInterval(), bootNonce, log);
        return new Wired(
                new Bus(core, registry, bootNonce),
                new Lifecycle(core, heartbeat, log, network),
                peers,
                duplicates,
                network,
                carriers,
                scheduler);
    }

    /**
     * A fresh random number for this boot. Drawn on every enable, so a reload counts as a new boot too; it only
     * has to differ between two backends running at once, which 64 random bits make all but certain.
     */
    private static long bootNonce() {
        SecureRandom random = new SecureRandom();
        long nonce;
        do {
            nonce = random.nextLong();
        } while (nonce == ServerPing.NO_NONCE);
        return nonce;
    }

    /**
     * Build the transport(s) named by {@code network.transport}: the proxy plugin-messaging carrier, the Redis
     * pub/sub carrier (a {@link DeferredRedisTransport} that resolves the companion plugin's factory through the
     * {@code ServicesManager} at start, so the bukkit-adapter never names a Lettuce type and the main jar never
     * bundles a Redis client), or a {@link CompositeBusTransport} fanning over both. An unrecognised value
     * already fell back to {@code velocity} in {@link NetworkConfig}; this logs the WARN so the operator sees
     * their typo without the enable crashing.
     */
    private static Carriers selectTransport(Plugin plugin, NetworkConfig network, Scheduler scheduler, Logger log) {
        if (!network.transportRecognized()) {
            log.warn(
                    "unknown network.transport value; falling back to {}. valid: velocity | redis | both",
                    network.transport().name().toLowerCase(java.util.Locale.ROOT));
        }
        return switch (network.transport()) {
            case VELOCITY -> {
                PluginMessagingTransport proxy = pluginMessaging(plugin, network, scheduler, log);
                yield new Carriers(proxy, proxy, null);
            }
            case REDIS -> {
                BusTransport redis = redis(plugin, network, scheduler, log);
                yield new Carriers(redis, null, redis);
            }
            case BOTH -> {
                PluginMessagingTransport proxy = pluginMessaging(plugin, network, scheduler, log);
                BusTransport redis = redis(plugin, network, scheduler, log);
                yield new Carriers(new CompositeBusTransport(proxy, redis), proxy, redis);
            }
        };
    }

    private static PluginMessagingTransport pluginMessaging(
            Plugin plugin, NetworkConfig network, Scheduler scheduler, Logger log) {
        return new PluginMessagingTransport(
                plugin, scheduler, network.channel(), network.outboundQueueSize(), network.peerLivenessWindow(), log);
    }

    /**
     * The Redis arm of the bus. The Redis transport ships in the optional {@code uxmEssentials-redis} companion
     * plugin, which publishes a {@code RedisTransportFactory} through the {@code ServicesManager} when it enables.
     * Because the companion loads after the host, the factory is not yet registered while this wiring runs, so we
     * hand back a {@link DeferredRedisTransport} that resolves the factory at start time (the host defers starting
     * the bus to its first global tick, past every plugin's enable). Companion present → the real Redis transport;
     * companion absent → a {@link LocalOnlyBusTransport} with one WARN, the transport degradation contract.
     */
    private static BusTransport redis(Plugin plugin, NetworkConfig network, Scheduler scheduler, Logger log) {
        return new DeferredRedisTransport(plugin, network.redis(), scheduler, log);
    }

    /**
     * The transport the core drives, plus the carriers inside it the doctor reports on one by one: with
     * {@code transport = both} one healthy carrier must not hide the other being down.
     */
    private record Carriers(
            BusTransport transport,
            @org.jspecify.annotations.Nullable PluginMessagingTransport proxy,
            @org.jspecify.annotations.Nullable BusTransport redis) {

        static final Carriers NONE = new Carriers(new LocalOnlyBusTransport(), null, null);
    }

    /**
     * The wired bus: the {@link Bus} handle contexts opt into sync through, plus the lifecycle of the live core
     * (absent for a disabled backend, in which case start/stop are no-ops). It deliberately exposes only the
     * {@link Bus}, the two lifecycle hooks, and the read-only {@link BusHealth} diagnostic view, never the
     * {@link BusCore} or the transport behind them.
     */
    public static final class Wired {

        private final Bus bus;
        private final @org.jspecify.annotations.Nullable Lifecycle lifecycle;
        private final @org.jspecify.annotations.Nullable ClusterPeers peers;
        private final @org.jspecify.annotations.Nullable DuplicateServerNames duplicates;
        private final NetworkConfig network;
        private final Carriers carriers;
        private final Scheduler scheduler;

        // Lifecycle gate for the deferred start. start() schedules the real start onto the first global tick;
        // stop() may run (a disable) before that tick fires. The gate makes the pair safe in either order: a
        // stop before the deferred start cancels it (the task sees STOPPED and never starts the core), and a
        // start that already ran is stopped normally. Owned here, read/written only on the global thread by the
        // deferred task and by stop() (itself called on disable, on the main/global thread).
        private enum Phase {
            PENDING,
            STARTED,
            STOPPED
        }

        private final java.util.concurrent.atomic.AtomicReference<Phase> phase =
                new java.util.concurrent.atomic.AtomicReference<>(Phase.PENDING);

        private Wired(
                Bus bus,
                @org.jspecify.annotations.Nullable Lifecycle lifecycle,
                @org.jspecify.annotations.Nullable ClusterPeers peers,
                @org.jspecify.annotations.Nullable DuplicateServerNames duplicates,
                NetworkConfig network,
                Carriers carriers,
                Scheduler scheduler) {
            this.bus = Objects.requireNonNull(bus, "bus");
            this.lifecycle = lifecycle;
            this.peers = peers;
            this.duplicates = duplicates;
            this.network = Objects.requireNonNull(network, "network");
            this.carriers = Objects.requireNonNull(carriers, "carriers");
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        }

        /** The publish + register seam handed to each context. */
        public Bus bus() {
            return bus;
        }

        /**
         * A read-only health view for {@code /uxmess doctor}: the bus's enabled flag, the configured transport
         * name, and live reads of each carrier in use. For a disabled backend the view reports
         * {@code enabled() == false} and has no carrier to read.
         */
        public BusHealth health() {
            return new WiredHealth(network, carriers);
        }

        /**
         * The cluster roster the cluster-peer health check reads, present only for an enabled backend. A disabled
         * backend has no roster (it neither sends nor records heartbeats), so this is empty and the check is not
         * registered for it.
         */
        public java.util.Optional<ClusterPeers> peers() {
            return java.util.Optional.ofNullable(peers);
        }

        /**
         * The watch for other backends using this backend's name, present only for an enabled backend: a disabled
         * one hears no heartbeats, so a shared name cannot hurt it and is not checked.
         */
        public java.util.Optional<DuplicateServerNames> duplicateServerNames() {
            return java.util.Optional.ofNullable(duplicates);
        }

        /**
         * Start the bus, deferred to the first global tick. The Redis transport resolves the companion plugin's
         * factory through the {@code ServicesManager}, and the companion is declared to load after the host, so
         * its factory is not registered yet while the host enables. Deferring the start to the first global tick
         * (past every plugin's enable) lets the redis arm find the factory; the proxy and disabled paths are
         * unaffected by the one-tick delay. A disabled backend has no lifecycle and schedules nothing.
         */
        public void start() {
            if (lifecycle == null) {
                return;
            }
            scheduler.onGlobal(() -> {
                if (phase.compareAndSet(Phase.PENDING, Phase.STARTED)) {
                    lifecycle.start();
                }
            });
        }

        /**
         * Unregister the channel and drop buffered frames; a no-op for a disabled backend. Safe in any order
         * against the deferred start: if the first tick has not fired, this flips the gate to STOPPED so the
         * pending start becomes a no-op; if the bus already started, it is stopped normally.
         */
        public void stop() {
            if (lifecycle == null) {
                return;
            }
            if (phase.getAndSet(Phase.STOPPED) == Phase.STARTED) {
                lifecycle.stop();
            }
        }
    }

    /**
     * The live core and presence heartbeat, plus the diagnostics logged on start. Starting the core registers
     * the plugin-messaging channel through the transport it owns and starts the heartbeat publishing this
     * backend's pings; stopping it unregisters the channel, drops the buffer, and stops the heartbeat.
     */
    private record Lifecycle(BusCore core, ClusterHeartbeat heartbeat, Logger log, NetworkConfig network) {

        private Lifecycle {
            Objects.requireNonNull(core, "core");
            Objects.requireNonNull(heartbeat, "heartbeat");
            Objects.requireNonNull(log, "log");
            Objects.requireNonNull(network, "network");
        }

        void start() {
            core.start();
            // The heartbeat starts after the channel is up so the first ping rides a live transport.
            heartbeat.start();
            log.info("network sync enabled on channel {} as server-id {}", network.channel(), network.serverId());
        }

        void stop() {
            // Stop the heartbeat before the channel goes away so no ping is published into a closed transport.
            heartbeat.stop();
            core.stop();
        }
    }

    /**
     * The {@link BusHealth} the doctor reads. {@code enabled}, {@code transport} and the answer window come from the
     * immutable {@link NetworkConfig} snapshot; the carrier states are live reads, so the doctor sees them as they are
     * at the moment it runs.
     */
    private record WiredHealth(NetworkConfig network, Carriers carriers) implements BusHealth {

        private WiredHealth {
            Objects.requireNonNull(network, "network");
            Objects.requireNonNull(carriers, "carriers");
        }

        @Override
        public boolean enabled() {
            return network.enabled();
        }

        @Override
        public String transport() {
            return network.transport().name().toLowerCase(java.util.Locale.ROOT);
        }

        @Override
        public java.util.Optional<ProxyLink> proxy() {
            return java.util.Optional.ofNullable(carriers.proxy()).map(PluginMessagingTransport::link);
        }

        @Override
        public java.util.Optional<Boolean> redisConnected() {
            return java.util.Optional.ofNullable(carriers.redis()).map(BusTransport::healthy);
        }

        @Override
        public java.time.Duration answerWindow() {
            return network.peerLivenessWindow();
        }
    }
}
