package com.uxplima.uxmessentials.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.network.BalanceChanged;
import com.uxplima.uxmessentials.shared.network.BanChanged;
import com.uxplima.uxmessentials.shared.network.HologramChanged;
import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.IgnoreChanged;
import com.uxplima.uxmessentials.shared.network.MuteChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.uxplima.uxmessentials.shared.network.NpcChanged;
import com.uxplima.uxmessentials.shared.network.PlayerWarpChanged;
import com.uxplima.uxmessentials.shared.network.ServerPing;
import com.uxplima.uxmessentials.shared.network.TradeSignalFrame;
import com.uxplima.uxmessentials.shared.network.VaultChanged;
import com.uxplima.uxmessentials.shared.network.VoteCounterChanged;
import com.uxplima.uxmessentials.shared.network.VotePartyFired;
import com.uxplima.uxmessentials.shared.network.WarpChanged;
import com.uxplima.uxmlib.redis.RedisBus;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Transport parity for the Redis path: every one of the {@link NetworkMessage} frame types survives the
 * {@link RedisBusTransportAdapter} seam byte-identically, over a fake {@link RedisBus} with no live Redis. The
 * transport carries opaque {@code byte[]}, so parity means the bytes published on a send equal
 * {@link NetworkMessageCodec#encode}, and the exact same bytes delivered on the subscribe path reach the
 * {@code onFrame} sink verbatim and decode back to a frame equal to the original.
 *
 * <p>The companion sweep over the plugin-messaging transport lives in {@code BusTransportParityTest} in the
 * bukkit adapter. Both sweeps walk the same {@link #oneOfEach() one-of-each} list and both assert the list size
 * and the set of {@link NetworkMessage.MessageType}s it covers equal {@link NetworkMessage.MessageType#values()},
 * so the set of frames proven over Redis, the set proven over plugin-messaging, and the full set of wire types
 * are one and the same. The codec's own round-trip across all types is covered in {@code NetworkMessageCodecTest}.
 */
class RedisBusTransportAdapterParityTest {

    private static final String CHANNEL = "uxmessentials:bus_v1";
    private static final String PEER = "lobby-2";
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    static Stream<NetworkMessage> everyFrame() {
        return oneOfEach().stream();
    }

    static List<NetworkMessage> oneOfEach() {
        return List.of(
                new BalanceChanged(PEER, OWNER, "coins"),
                new HomeChanged(PEER, OWNER),
                new WarpChanged(PEER, "spawn"),
                new VaultChanged(PEER, OWNER, 3),
                new ServerPing(PEER, 1_717_000_000_000L),
                new VotePartyFired(PEER, 25),
                new VoteCounterChanged(PEER),
                new BanChanged(PEER, TARGET),
                new MuteChanged(PEER, TARGET),
                new PlayerWarpChanged(PEER, OWNER),
                new HologramChanged(PEER, "lobby-board"),
                new NpcChanged(PEER, "guide"),
                new IgnoreChanged(PEER, OWNER),
                new TradeSignalFrame(
                        PEER,
                        UUID.fromString("00000000-0000-0000-0000-0000000000cc"),
                        "READY",
                        OWNER,
                        "Alice",
                        TARGET,
                        "Bob"));
    }

    @ParameterizedTest
    @MethodSource("everyFrame")
    void sendPublishesTheExactCodecBytes(NetworkMessage frame) {
        CapturingChannel channel = new CapturingChannel();
        RedisBusTransportAdapter transport = new RedisBusTransportAdapter(channel, CHANNEL, new RecordingLogger());
        transport.start(received -> {});

        byte[] encoded = NetworkMessageCodec.encode(frame);
        transport.send(encoded);

        assertThat(channel.published()).hasSize(1);
        assertThat(channel.published().get(0).channel()).isEqualTo(CHANNEL);
        assertThat(channel.published().get(0).frame()).isEqualTo(encoded);
    }

    @ParameterizedTest
    @MethodSource("everyFrame")
    void aDeliveredFrameReachesOnFrameVerbatimAndDecodesEqual(NetworkMessage frame) {
        CapturingChannel channel = new CapturingChannel();
        RedisBusTransportAdapter transport = new RedisBusTransportAdapter(channel, CHANNEL, new RecordingLogger());
        List<byte[]> received = new ArrayList<>();
        transport.start(received::add);

        byte[] encoded = NetworkMessageCodec.encode(frame);
        channel.feed(encoded);

        assertThat(received).hasSize(1);
        assertThat(received.get(0)).isEqualTo(encoded);
        assertThat(NetworkMessageCodec.decode(received.get(0))).isEqualTo(frame);
    }

    @Test
    void theSweepCoversEveryWireType() {
        assertThat(oneOfEach())
                .hasSize(NetworkMessage.MessageType.values().length)
                .extracting(NetworkMessage::type)
                .containsExactlyInAnyOrder(NetworkMessage.MessageType.values());
    }

    /** A stand-in for the Redis wire: captures the inbound consumer and every published channel/frame pair. */
    private static final class CapturingChannel implements RedisBus {

        private @Nullable Consumer<byte[]> onFrame;
        private final List<Published> published = new ArrayList<>();

        @Override
        public void publish(String channel, byte[] frame) {
            published.add(new Published(channel, frame));
        }

        @Override
        public void subscribe(String channel, Consumer<byte[]> onFrame) {
            this.onFrame = onFrame;
        }

        @Override
        public void close() {}

        void feed(byte[] frame) {
            Consumer<byte[]> sink = onFrame;
            if (sink == null) {
                throw new IllegalStateException("no active subscriber to feed");
            }
            sink.accept(frame);
        }

        List<Published> published() {
            return published;
        }
    }

    /** A captured publish; a plain holder because Error Prone forbids array record components. */
    private static final class Published {
        private final String channel;
        private final byte[] frame;

        Published(String channel, byte[] frame) {
            this.channel = channel;
            this.frame = frame;
        }

        String channel() {
            return channel;
        }

        byte[] frame() {
            return frame;
        }
    }
}
