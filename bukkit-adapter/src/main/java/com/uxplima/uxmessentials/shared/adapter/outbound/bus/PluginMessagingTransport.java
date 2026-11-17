package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.BedrockFormAnswerFrame;
import com.uxplima.uxmessentials.shared.network.BedrockPresenceFrame;
import com.uxplima.uxmessentials.shared.network.BusChannel;
import com.uxplima.uxmessentials.shared.network.BusSecret;
import com.uxplima.uxmessentials.shared.network.BusTransport;
import com.uxplima.uxmessentials.shared.network.FrameSeal;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.uxplima.uxmessentials.shared.network.ProxyConnectionFrame;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The plugin-messaging {@link BusTransport}: it moves opaque frame bytes over a carrier player's connection
 * through the proxy. Registers the plugin-messaging channel with Bukkit's {@code Messenger}, buffers outbound
 * frames and flushes them on any online player, and feeds every trusted inbound frame's bytes back to the
 * {@code onFrame} sink the {@link BusCore} above the seam registers. It knows nothing about
 * {@code NetworkMessage} or the codec, the encode/decode, origin stamp, self-origin loop sentinel and listener
 * dispatch all live above this seam in {@link BusCore}; only the byte-moving machinery is here.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b> for the outbound buffer ({@link OutboundFrames}, guarded by its own
 * monitor for the small bounded push/drain). At most one flush waits on the global thread at a time, however many
 * frames are sent before it runs. Every Bukkit touch. Registering the channel, sending a frame
 * through a carrier player. Hops onto the right thread through the injected {@link Scheduler} port; the
 * inbound hand-off runs off the tick thread via {@link Scheduler#async}. The transport never blocks a region
 * thread and never calls a Bukkit API off it.
 *
 * <h2>Degradation</h2>
 * Plugin messages ride a player connection, so a frame can only leave once a player is online to carry it.
 * With no proxy, no peers, or no online players the buffered frames simply never drain and the bus is a no-op,
 * the plugin runs fully local. This is the "degrades to local-only when no proxy/peer responds" contract:
 * nothing about the single-server happy path depends on the transport. A queue that fills while it waits drops the
 * frames whose loss costs least first, and says so in the log; see {@link OutboundFrames}.
 *
 * <h2>Trust</h2>
 * Whatever sits in front of a backend decides whether a player's client can write on the channel: the
 * {@code uxmEssentials-velocity} broker stops it, but a proxy without the broker, a BungeeCord proxy, or a backend
 * players reach directly passes it on. So every frame is sealed with the shared {@link BusSecret} when it leaves, and
 * an inbound frame reaches the core only when its {@link FrameSeal} checks out; the rest are counted and reported by
 * {@link RefusedFrameReport}. Without a usable secret the channel is never registered: nothing is sent, and nothing a
 * client writes is read.
 *
 * <h2>Proxy contact</h2>
 * A registered channel proves nothing about the proxy: without the broker every frame leaves and none comes back from
 * another backend. With the broker, a frame arrives as a peer's frame or as this backend's own heartbeat, which the
 * broker returns to its sender. {@link #link()} reports when a signed frame last arrived, which {@code /uxmess doctor}
 * reads as the proxy answering. A client that receives this backend's frames (with no broker in front) could hand a
 * heartbeat straight back, so it stays a diagnostic; that client still cannot forge a frame the core acts on.
 * {@link #link()} also says whether the last flush found nobody online to carry the queue, the other reason frames
 * stop.
 */
@NullMarked
public final class PluginMessagingTransport implements PluginMessageListener, BusTransport {

    private static final long NEVER = Long.MIN_VALUE;

    private final PluginChannel pluginChannel;
    private final Scheduler scheduler;
    private final String channel;
    private final OutboundFrames outbound;
    private final AtomicBoolean flushPending = new AtomicBoolean();
    private final OversizeFrameReport oversize;
    private final LongSupplier clock;
    private final Duration answerWindow;
    private final @Nullable FrameSeal seal;
    private final RefusedFrameReport refusals;
    private final CarrierReceipts receipts;

    private volatile boolean running;
    private volatile long lastHeardMillis = NEVER;
    private volatile boolean waitingForPlayer;
    private @Nullable Consumer<byte[]> onFrame;

    /**
     * A transport on {@code channel} that signs and checks frames with {@code secret}, and counts the proxy as
     * answering while a signed frame came back within {@code answerWindow}, a few heartbeats, so one late frame does
     * not flip it. A secret that is not {@link BusSecret#usable() usable} leaves the channel closed.
     */
    public PluginMessagingTransport(
            Plugin plugin,
            Scheduler scheduler,
            String channel,
            int outboundQueueSize,
            Duration answerWindow,
            BusSecret secret,
            CarrierReceipts receipts,
            Logger log) {
        this(
                new BukkitChannel(plugin, channel),
                scheduler,
                channel,
                outboundQueueSize,
                answerWindow,
                secret,
                receipts,
                log,
                System::currentTimeMillis);
    }

    PluginMessagingTransport(
            PluginChannel pluginChannel,
            Scheduler scheduler,
            String channel,
            int outboundQueueSize,
            Duration answerWindow,
            BusSecret secret,
            CarrierReceipts receipts,
            Logger log,
            LongSupplier clock) {
        Objects.requireNonNull(secret, "secret");
        this.receipts = Objects.requireNonNull(receipts, "receipts");
        this.oversize = new OversizeFrameReport(Objects.requireNonNull(log, "log"), clock);
        this.refusals = new RefusedFrameReport(log, clock);
        this.seal = secret.usable() ? new FrameSeal(secret, clock) : null;
        this.pluginChannel = Objects.requireNonNull(pluginChannel, "pluginChannel");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.channel = Objects.requireNonNull(channel, "channel");
        if (outboundQueueSize < 1) {
            throw new IllegalArgumentException("outboundQueueSize must be positive: " + outboundQueueSize);
        }
        this.outbound = new OutboundFrames(outboundQueueSize, new DroppedFrameReport(log, clock));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.answerWindow = Objects.requireNonNull(answerWindow, "answerWindow");
    }

    /**
     * Register the plugin-messaging channel and route the bytes of every trusted inbound frame to {@code onFrame}.
     * Without a usable secret nothing is registered and the transport stays stopped.
     */
    @Override
    public void start(Consumer<byte[]> onFrame) {
        this.onFrame = Objects.requireNonNull(onFrame, "onFrame");
        if (seal == null) {
            return;
        }
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
        outbound.clear();
        lastHeardMillis = NEVER;
        waitingForPlayer = false;
        refusals.reset();
    }

    /**
     * Buffer one already-encoded frame and schedule a flush unless one is already waiting. Bounded: when the buffer is
     * full a frame is dropped and reported rather than pinning memory, as {@link OutboundFrames} chooses. A no-op when
     * the transport is not running (stopped or never started), so a publish before start never touches the scheduler.
     */
    @Override
    public void send(byte[] frame) {
        Objects.requireNonNull(frame, "frame");
        if (!running) {
            return;
        }
        int sealedLength = frame.length + FrameSeal.TRAILER_BYTES;
        if (sealedLength > BusChannel.MAX_FRAME_BYTES) {
            // The proxy relays the frame serverbound, and a backend disconnects the carrier for a payload this large.
            oversize.dropped(sealedLength);
            return;
        }
        outbound.add(frame);
        if (flushPending.compareAndSet(false, true)) {
            try {
                scheduler.onGlobal(this::flush);
            } catch (RuntimeException refused) {
                // A flush that was never scheduled must not keep every later send from scheduling one.
                flushPending.set(false);
                throw refused;
            }
        }
    }

    /**
     * True when the channel is registered and a signed frame came back through the proxy within the answer window. A
     * registered channel alone is not enough, since frames leave whether or not anything on the proxy relays them.
     */
    @Override
    public boolean healthy() {
        return link().answeredWithin(clock.getAsLong(), answerWindow);
    }

    /**
     * What the doctor reports for this carrier: whether it has a secret to sign with, whether the channel is up, when
     * the proxy last answered, whether the last flush found nobody online to carry the queue, and what was refused.
     */
    BusHealth.ProxyLink link() {
        long heard = lastHeardMillis;
        return new BusHealth.ProxyLink(
                seal != null,
                running,
                heard == NEVER ? OptionalLong.empty() : OptionalLong.of(heard),
                waitingForPlayer,
                refusals.count(),
                refusals.last());
    }

    @Override
    public void onPluginMessageReceived(String inboundChannel, Player carrier, byte[] frame) {
        if (running && channel.equals(inboundChannel)) {
            noteCarrier(carrier.getUniqueId(), frame);
        }
        received(inboundChannel, frame);
    }

    /**
     * Note, here on the carrier's own thread and before the frame is checked, whose connection carried a frame the
     * proxy plugin sends about that same player, and when: the listener that acts on it learns from the receipt whether
     * it came over that player's connection and during which of their visits. Frames of other kinds, about another
     * player, or that do not decode are not noted; a receipt nobody takes is dropped by newer ones.
     */
    void noteCarrier(UUID carrier, byte[] sealed) {
        if (sealed.length <= FrameSeal.TRAILER_BYTES || !fromProxyPlugin(sealed)) {
            return;
        }
        try {
            switch (NetworkMessageCodec.decode(FrameSeal.frame(sealed))) {
                case ProxyConnectionFrame frame
                when frame.player().equals(carrier) -> receipts.received(frame.frameId(), carrier);
                case BedrockPresenceFrame frame
                when frame.player().equals(carrier) -> receipts.received(frame.frameId(), carrier);
                case BedrockFormAnswerFrame frame
                when frame.player().equals(carrier) -> receipts.received(frame.frameId(), carrier);
                default -> {
                    // About another player: not noted.
                }
            }
        } catch (RuntimeException unreadable) {
            // Not a frame this build reads; the signature check refuses and counts it.
        }
    }

    /** Whether {@code frame} is of a kind only the proxy plugin sends, over the connection of the player it names. */
    static boolean fromProxyPlugin(byte[] frame) {
        return NetworkMessageCodec.isType(frame, NetworkMessage.MessageType.PROXY_CONNECTION)
                || NetworkMessageCodec.isType(frame, NetworkMessage.MessageType.BEDROCK_PRESENCE)
                || NetworkMessageCodec.isType(frame, NetworkMessage.MessageType.BEDROCK_FORM_ANSWER);
    }

    /**
     * Take one inbound frame off the tick thread: check its seal, then note that the proxy answered and hand the frame
     * to the core, or count it as refused.
     */
    void received(String inboundChannel, byte[] frame) {
        Consumer<byte[]> sink = onFrame;
        FrameSeal check = seal;
        if (!running || sink == null || check == null || !channel.equals(inboundChannel)) {
            return;
        }
        // Off the tick thread: this handler may run on the carrier's region thread, and the signature check and the
        // core's decode + dispatch do no Bukkit work.
        byte[] copy = frame.clone();
        scheduler.async(() -> {
            if (!running) {
                return;
            }
            FrameSeal.Check verdict = check.check(copy);
            if (verdict != FrameSeal.Check.TRUSTED) {
                refusals.refused(verdict);
                return;
            }
            lastHeardMillis = clock.getAsLong();
            sink.accept(FrameSeal.frame(copy));
        });
    }

    private void flush() {
        // Cleared before the drain, so a frame sent while it runs schedules the next flush instead of being missed.
        flushPending.set(false);
        FrameSeal sealer = seal;
        if (sealer == null) {
            return;
        }
        Consumer<byte[]> carrier = pluginChannel.carrier();
        // Read on the global thread, where the online list may be asked; the doctor only reads the flag.
        waitingForPlayer = carrier == null;
        if (carrier == null) {
            return;
        }
        // Drain the buffer through one carrier. The proxy routes by channel, not by the carrier identity, so any
        // online player can carry a server-wide frame.
        // Sealed as it leaves, not when queued, so a frame that waited for a player is not stale on arrival.
        for (byte[] frame = outbound.poll(); frame != null; frame = outbound.poll()) {
            carrier.accept(sealer.seal(frame));
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
