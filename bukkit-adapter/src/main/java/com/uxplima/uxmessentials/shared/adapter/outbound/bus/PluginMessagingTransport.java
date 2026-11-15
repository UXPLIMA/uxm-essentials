package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.BusChannel;
import com.uxplima.uxmessentials.shared.network.BusTransport;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The plugin-messaging {@link BusTransport}: it moves opaque frame bytes over a carrier player's connection
 * through the proxy. Registers the plugin-messaging channel with Bukkit's {@code Messenger}, buffers outbound
 * frames and flushes them on any online player, and feeds every inbound frame's bytes back to the
 * {@code onFrame} sink the {@link BusCore} above the seam registers. It knows nothing about
 * {@code NetworkMessage} or the codec, the encode/decode, origin stamp, self-origin loop sentinel and listener
 * dispatch all live above this seam in {@link BusCore}; only the byte-moving machinery is here.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b> for the outbound buffer ({@link #outbound}, guarded by its own
 * monitor for the small bounded push/drain). Every Bukkit touch. Registering the channel, sending a frame
 * through a carrier player. Hops onto the right thread through the injected {@link Scheduler} port; the
 * inbound hand-off runs off the tick thread via {@link Scheduler#async}. The transport never blocks a region
 * thread and never calls a Bukkit API off it.
 *
 * <h2>Degradation</h2>
 * Plugin messages ride a player connection, so a frame can only leave once a player is online to carry it.
 * With no proxy, no peers, or no online players the buffered frames simply never drain and the bus is a no-op,
 * the plugin runs fully local. This is the "degrades to local-only when no proxy/peer responds" contract:
 * nothing about the single-server happy path depends on the transport.
 *
 * <h2>Proxy contact</h2>
 * A registered channel proves nothing about the proxy: without the {@code uxmEssentials-velocity} broker every frame
 * leaves and none comes back from another backend. With the broker, a frame arrives as a peer's frame or as this
 * backend's own heartbeat, which the broker returns to its sender. {@link #link()} reports when a frame last arrived,
 * which {@code /uxmess doctor} reads as the proxy answering. It is a diagnostic: without the broker Velocity passes a
 * client's plugin messages on to the backend, so a frame on the channel is evidence of the broker, not proof.
 * {@link #link()} also says whether the last flush found nobody online to carry the queue, the other reason frames
 * stop.
 */
@NullMarked
public final class PluginMessagingTransport implements PluginMessageListener, BusTransport {

    private static final byte[] EMPTY = new byte[0];
    private static final long NEVER = Long.MIN_VALUE;

    private final PluginChannel pluginChannel;
    private final Scheduler scheduler;
    private final String channel;
    private final int outboundQueueSize;
    private final Deque<byte[]> outbound = new ArrayDeque<>();
    private final OversizeFrameReport oversize;
    private final LongSupplier clock;
    private final Duration answerWindow;

    private volatile boolean running;
    private volatile long lastHeardMillis = NEVER;
    private volatile boolean waitingForPlayer;
    private @Nullable Consumer<byte[]> onFrame;

    /**
     * A transport on {@code channel} that counts the proxy as answering while a frame came back within
     * {@code answerWindow}, a few heartbeats, so one late frame does not flip it.
     */
    public PluginMessagingTransport(
            Plugin plugin,
            Scheduler scheduler,
            String channel,
            int outboundQueueSize,
            Duration answerWindow,
            Logger log) {
        this(
                new BukkitChannel(plugin, channel),
                scheduler,
                channel,
                outboundQueueSize,
                answerWindow,
                log,
                System::currentTimeMillis);
    }

    PluginMessagingTransport(
            PluginChannel pluginChannel,
            Scheduler scheduler,
            String channel,
            int outboundQueueSize,
            Duration answerWindow,
            Logger log,
            LongSupplier clock) {
        this.oversize = new OversizeFrameReport(Objects.requireNonNull(log, "log"), clock);
        this.pluginChannel = Objects.requireNonNull(pluginChannel, "pluginChannel");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.channel = Objects.requireNonNull(channel, "channel");
        if (outboundQueueSize < 1) {
            throw new IllegalArgumentException("outboundQueueSize must be positive: " + outboundQueueSize);
        }
        this.outboundQueueSize = outboundQueueSize;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.answerWindow = Objects.requireNonNull(answerWindow, "answerWindow");
    }

    /** Register the plugin-messaging channel and route every inbound frame's bytes to {@code onFrame}. */
    @Override
    public void start(Consumer<byte[]> onFrame) {
        this.onFrame = Objects.requireNonNull(onFrame, "onFrame");
        pluginChannel.open(this);
        running = true;
    }

    /** Unregister the channel and drop any buffered frames and what the proxy last said. Idempotent. */
    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        pluginChannel.close(this);
        synchronized (outbound) {
            outbound.clear();
        }
        lastHeardMillis = NEVER;
        waitingForPlayer = false;
    }

    /**
     * Buffer one already-encoded frame and schedule a flush. Bounded: when the buffer is full the oldest frame
     * is dropped rather than pinning memory. A no-op when the transport is not running (stopped or never
     * started), so a publish before start never touches the scheduler.
     */
    @Override
    public void send(byte[] frame) {
        Objects.requireNonNull(frame, "frame");
        if (!running) {
            return;
        }
        if (frame.length > BusChannel.MAX_FRAME_BYTES) {
            // The proxy relays the frame serverbound, and a backend disconnects the carrier for a payload this large.
            oversize.dropped(frame.length);
            return;
        }
        synchronized (outbound) {
            while (outbound.size() >= outboundQueueSize) {
                outbound.pollFirst();
            }
            outbound.addLast(frame);
        }
        scheduler.onGlobal(this::flush);
    }

    /**
     * True when the channel is registered and a frame came back through the proxy within the answer window. A
     * registered channel alone is not enough, since frames leave whether or not anything on the proxy relays them.
     */
    @Override
    public boolean healthy() {
        return link().answeredWithin(clock.getAsLong(), answerWindow);
    }

    /**
     * What the doctor reports for this carrier: whether the channel is up, when the proxy last answered, and whether
     * the last flush found nobody online to carry the queue.
     */
    BusHealth.ProxyLink link() {
        long heard = lastHeardMillis;
        return new BusHealth.ProxyLink(
                running, heard == NEVER ? OptionalLong.empty() : OptionalLong.of(heard), waitingForPlayer);
    }

    @Override
    public void onPluginMessageReceived(String inboundChannel, Player carrier, byte[] frame) {
        received(inboundChannel, frame);
    }

    /** Take one inbound frame: note that the proxy answered, then hand the bytes to the core off the tick thread. */
    void received(String inboundChannel, byte[] frame) {
        Consumer<byte[]> sink = onFrame;
        if (!running || sink == null || !channel.equals(inboundChannel)) {
            return;
        }
        lastHeardMillis = clock.getAsLong();
        // Hand the bytes off the tick thread: this handler may run on the carrier's region thread and the core's
        // decode + dispatch does cache work, not Bukkit work.
        byte[] copy = frame.clone();
        scheduler.async(() -> sink.accept(copy));
    }

    private void flush() {
        Consumer<byte[]> carrier = pluginChannel.carrier();
        // Read on the global thread, where the online list may be asked; the doctor only reads the flag.
        waitingForPlayer = carrier == null;
        if (carrier == null) {
            return;
        }
        // Drain the buffer through one carrier. The proxy routes by channel, not by the carrier identity, so any
        // online player can carry a server-wide frame.
        for (byte[] frame = drainOne(); frame.length > 0; frame = drainOne()) {
            carrier.accept(frame);
        }
    }

    private byte[] drainOne() {
        synchronized (outbound) {
            byte[] frame = outbound.pollFirst();
            // An empty array is the "nothing left" sentinel: a real frame always carries the version byte.
            return frame == null ? EMPTY : frame;
        }
    }

    /** The server side of the channel: registering it, and a player a frame can ride on. */
    interface PluginChannel {

        /** Register the channel both ways, delivering inbound frames to {@code listener}. */
        void open(PluginMessageListener listener);

        /** Unregister what {@link #open} registered. */
        void close(PluginMessageListener listener);

        /** A sender bound to one online player, or null with nobody online. Called on the global thread. */
        @Nullable Consumer<byte[]> carrier();
    }

    /** The {@link PluginChannel} over Bukkit's {@code Messenger} and online players. */
    private record BukkitChannel(Plugin plugin, String channel) implements PluginChannel {

        private BukkitChannel {
            Objects.requireNonNull(plugin, "plugin");
            Objects.requireNonNull(channel, "channel");
        }

        @Override
        public void open(PluginMessageListener listener) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, channel, listener);
        }

        @Override
        public void close(PluginMessageListener listener) {
            plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, channel, listener);
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, channel);
        }

        @Override
        public @Nullable Consumer<byte[]> carrier() {
            Collection<? extends Player> online = plugin.getServer().getOnlinePlayers();
            if (online.isEmpty()) {
                return null;
            }
            Player player = online.iterator().next();
            return frame -> player.sendPluginMessage(plugin, channel, frame);
        }
    }
}
