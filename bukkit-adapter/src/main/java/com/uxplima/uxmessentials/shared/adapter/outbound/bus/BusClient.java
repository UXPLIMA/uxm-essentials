package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.BusTransport;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import org.jspecify.annotations.NullMarked;

/**
 * The plugin-messaging {@link BusTransport} for the cross-server bus: it moves opaque frame bytes over a
 * carrier player's connection through the proxy. Registers the plugin-messaging channel with Bukkit's
 * {@code Messenger}, buffers outbound frames and flushes them on any online player, and feeds every inbound
 * frame's bytes back up to the transport-agnostic {@link BusCore}. The codec/dispatch half — encoding a
 * {@link NetworkMessage}, the self-origin loop sentinel, listener dispatch — lives in the {@link BusCore} this
 * client owns and delegates to; only the byte-moving machinery is here.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b> for the outbound buffer ({@link #outbound}, guarded by its own
 * monitor for the small bounded push/drain). Every Bukkit touch — registering the channel, sending a frame
 * through a carrier player — hops onto the right thread through the injected {@link Scheduler} port; the
 * inbound dispatch runs off the tick thread via {@link Scheduler#async}. The bus never blocks a region thread
 * and never calls a Bukkit API off it.
 *
 * <h2>Replication-loop sentinel</h2>
 * Every outbound frame is stamped with this backend's {@code server-id} as its origin. The {@link BusCore}
 * drops any inbound frame whose origin equals its own id, so a backend can never act on its own mutation
 * echoed back through the proxy ({@code docs/02-concurrency.md}). The proxy broker fans a frame out to every
 * backend but its origin; the core's self-origin drop is the second, independent guard.
 *
 * <h2>Degradation</h2>
 * Plugin messages ride a player connection, so a frame can only leave once a player is online to carry it.
 * With no proxy, no peers, or no online players the buffered frames simply never drain and the bus is a
 * no-op — the plugin runs fully local. This is the "degrades to local-only when no proxy/peer responds"
 * contract: nothing about the single-server happy path depends on the bus.
 */
@NullMarked
public final class BusClient implements PluginMessageListener, BusTransport, BusPublisher {

    private static final byte[] EMPTY = new byte[0];

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final Logger log;
    private final NetworkConfig config;
    private final BusCore core;
    private final Deque<byte[]> outbound = new ArrayDeque<>();

    private volatile boolean running;
    private @org.jspecify.annotations.Nullable Consumer<byte[]> onFrame;

    public BusClient(
            Plugin plugin, Scheduler scheduler, Logger log, NetworkConfig config, RemoteSyncRegistry registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.config = Objects.requireNonNull(config, "config");
        Objects.requireNonNull(registry, "registry");
        this.core = new BusCore(this, config.serverId(), registry, log);
    }

    // --- Lifecycle (driven by BusWiring) -------------------------------------------------------------------

    /**
     * Bring the bus up: register the channel through the transport and route inbound bytes into the core.
     * A no-op when the bus is disabled in {@code network.conf} — a disabled backend wires no channel and
     * holds no bus state, exactly like a single-server install.
     */
    public void start() {
        if (!config.enabled()) {
            log.info("network sync disabled (network.conf > enabled=false); bus runs local-only");
            return;
        }
        core.start();
        log.info("network sync enabled on channel {} as server-id {}", config.channel(), config.serverId());
    }

    /** Tear the bus down through the core: unregister the channel and drop buffered frames. Idempotent. */
    public void shutdown() {
        core.stop();
    }

    // --- BusPublisher (outbound seam contexts hold) --------------------------------------------------------

    /**
     * Stamp {@code message} with this backend's origin and queue it for delivery to peers. The frame leaves
     * the next time a carrier player is available; if none ever is, it is dropped from the bounded buffer
     * rather than pinning memory (degraded local-only). A no-op when the bus is disabled.
     */
    @Override
    public void publish(NetworkMessage message) {
        core.publish(message);
    }

    /** This backend's {@code server-id} — the origin stamped into outbound frames and the loop sentinel. */
    @Override
    public String serverId() {
        return core.serverId();
    }

    // --- BusTransport (the plugin-messaging machinery the core drives) ------------------------------------

    /** Register the plugin-messaging channel and route every inbound frame's bytes to {@code onFrame}. */
    @Override
    public void start(Consumer<byte[]> onFrame) {
        this.onFrame = Objects.requireNonNull(onFrame, "onFrame");
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, config.channel());
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, config.channel(), this);
        running = true;
    }

    /** Unregister the channel and drop any buffered frames. Idempotent. */
    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, config.channel(), this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, config.channel());
        synchronized (outbound) {
            outbound.clear();
        }
    }

    /**
     * Buffer one already-encoded frame and schedule a flush. Bounded: when the buffer is full the oldest
     * frame is dropped rather than pinning memory. A no-op when the transport is not running (disabled bus or
     * stopped), so a publish on a disabled backend never touches the scheduler.
     */
    @Override
    public void send(byte[] frame) {
        Objects.requireNonNull(frame, "frame");
        if (!running) {
            return;
        }
        synchronized (outbound) {
            while (outbound.size() >= config.outboundQueueSize()) {
                outbound.pollFirst();
            }
            outbound.addLast(frame);
        }
        scheduler.onGlobal(this::flush);
    }

    /** True when the channel is registered and a frame can be carried as soon as a player is online. */
    @Override
    public boolean healthy() {
        return running;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player carrier, byte[] frame) {
        Consumer<byte[]> sink = onFrame;
        if (!running || sink == null || !config.channel().equals(channel)) {
            return;
        }
        // Decode + dispatch off the tick thread: this handler may run on the carrier's region thread and a
        // listener does cache work, not Bukkit work.
        byte[] copy = frame.clone();
        scheduler.async(() -> sink.accept(copy));
    }

    private void flush() {
        Player carrier = anyCarrier();
        if (carrier == null) {
            return;
        }
        // Drain the buffer through one carrier. The proxy routes by channel, not by the carrier identity, so
        // any online player can carry a server-wide frame.
        for (byte[] frame = drainOne(); frame.length > 0; frame = drainOne()) {
            carrier.sendPluginMessage(plugin, config.channel(), frame);
        }
    }

    private byte[] drainOne() {
        synchronized (outbound) {
            byte[] frame = outbound.pollFirst();
            // An empty array is the "nothing left" sentinel — a real frame always carries the version byte.
            return frame == null ? EMPTY : frame;
        }
    }

    private @org.jspecify.annotations.Nullable Player anyCarrier() {
        Collection<? extends Player> online = plugin.getServer().getOnlinePlayers();
        return online.isEmpty() ? null : online.iterator().next();
    }
}
