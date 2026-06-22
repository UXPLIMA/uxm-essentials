package com.uxplima.uxmessentials.persistence.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
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
import com.uxplima.uxmessentials.shared.network.VaultChanged;
import com.uxplima.uxmessentials.shared.network.VoteCounterChanged;
import com.uxplima.uxmessentials.shared.network.VotePartyFired;
import com.uxplima.uxmessentials.shared.network.WarpChanged;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Transport parity for the Redis path: every one of the thirteen {@link NetworkMessage} frame types survives
 * the {@link RedisBusTransport} seam byte-identically, over a mock Jedis pool with no live Redis. The transport
 * carries opaque {@code byte[]}, so parity means the bytes published to the Redis channel on a send equal
 * {@link NetworkMessageCodec#encode}, and the exact same bytes delivered on the subscribe connection reach the
 * {@code onFrame} sink verbatim and decode back to a frame equal to the original.
 *
 * <p>The companion sweep over the plugin-messaging transport lives in {@code BusTransportParityTest} in the
 * bukkit adapter. Both sweeps walk the same {@link #oneOfEach() one-of-each} list and both assert the list size
 * and the set of {@link NetworkMessage.MessageType}s it covers equal {@link NetworkMessage.MessageType#values()},
 * so the set of frames proven over Redis, the set proven over plugin-messaging, and the full set of wire types
 * are one and the same. The codec's own round-trip across all thirteen is covered in
 * {@code NetworkMessageCodecTest}.
 */
class RedisBusTransportParityTest {

    private static final String CHANNEL = "uxmessentials:bus_v1";
    private static final String PEER = "lobby-2";
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private final ExecutorService async = Executors.newCachedThreadPool();

    @AfterEach
    void shutdown() {
        async.shutdownNow();
    }

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
                new IgnoreChanged(PEER, OWNER));
    }

    @ParameterizedTest
    @MethodSource("everyFrame")
    void sendPublishesTheExactCodecBytes(NetworkMessage frame) {
        FakeRedis redis = new FakeRedis();
        RedisBusTransport transport = transportOver(redis);
        transport.start(received -> {});

        byte[] encoded = NetworkMessageCodec.encode(frame);
        transport.send(encoded);

        waitUntil(() -> !redis.published().isEmpty());
        assertThat(redis.published()).hasSize(1);
        Published only = redis.published().get(0);
        assertThat(only.channel()).isEqualTo(CHANNEL.getBytes(StandardCharsets.UTF_8));
        assertThat(only.frame()).isEqualTo(encoded);

        transport.stop();
        redis.releaseSubscribe();
    }

    @ParameterizedTest
    @MethodSource("everyFrame")
    void aDeliveredFrameReachesOnFrameVerbatimAndDecodesEqual(NetworkMessage frame) {
        FakeRedis redis = new FakeRedis();
        RedisBusTransport transport = transportOver(redis);
        CopyOnWriteArrayList<byte[]> received = new CopyOnWriteArrayList<>();
        transport.start(received::add);
        waitUntil(() -> redis.subscriber() != null);

        byte[] encoded = NetworkMessageCodec.encode(frame);
        redis.deliver(encoded);

        assertThat(received).hasSize(1);
        assertThat(received.get(0)).isEqualTo(encoded);
        assertThat(NetworkMessageCodec.decode(received.get(0))).isEqualTo(frame);

        transport.stop();
        redis.releaseSubscribe();
    }

    @Test
    void theSweepCoversEveryWireType() {
        assertThat(oneOfEach())
                .hasSize(NetworkMessage.MessageType.values().length)
                .extracting(NetworkMessage::type)
                .containsExactlyInAnyOrder(NetworkMessage.MessageType.values());
    }

    private RedisBusTransport transportOver(FakeRedis redis) {
        return new RedisBusTransport(
                "localhost", 6379, "", 0, CHANNEL, new InlineAsyncScheduler(async), new NoopLogger(), redis::pool);
    }

    private static void waitUntil(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 2s");
            }
            Thread.onSpinWait();
        }
    }

    /**
     * A stand-in for the Redis server: a mock {@link JedisPool}/{@link Jedis} whose {@code subscribe} parks on a
     * latch until {@code releaseSubscribe} frees it, capturing the subscriber so the test can drive inbound
     * frames, and whose {@code publish} records every channel/frame pair.
     */
    private static final class FakeRedis {

        private final JedisPool pool = mock(JedisPool.class);
        private final Jedis jedis = mock(Jedis.class);
        private final AtomicReference<@Nullable BinaryJedisPubSub> subscriber = new AtomicReference<>();
        private final CountDownLatch subscribeLatch = new CountDownLatch(1);
        private final CopyOnWriteArrayList<Published> published = new CopyOnWriteArrayList<>();

        FakeRedis() {
            when(pool.getResource()).thenReturn(jedis);
            doAnswer(invocation -> {
                        BinaryJedisPubSub sub = invocation.getArgument(0);
                        subscriber.set(sub);
                        subscribeLatch.await();
                        return null;
                    })
                    .when(jedis)
                    .subscribe(any(BinaryJedisPubSub.class), any(byte[][].class));
            doAnswer(invocation -> {
                        published.add(new Published(invocation.getArgument(0), invocation.getArgument(1)));
                        return 1L;
                    })
                    .when(jedis)
                    .publish(any(byte[].class), any(byte[].class));
        }

        JedisPool pool() {
            return pool;
        }

        @Nullable BinaryJedisPubSub subscriber() {
            return subscriber.get();
        }

        void deliver(byte[] frame) {
            BinaryJedisPubSub sub = subscriber.get();
            if (sub == null) {
                throw new IllegalStateException("no active subscriber to deliver to");
            }
            sub.onMessage(CHANNEL.getBytes(StandardCharsets.UTF_8), frame);
        }

        void releaseSubscribe() {
            subscribeLatch.countDown();
        }

        CopyOnWriteArrayList<Published> published() {
            return published;
        }
    }

    /** A captured publish; a plain holder because Error Prone forbids array record components. */
    private static final class Published {
        private final byte[] channel;
        private final byte[] frame;

        Published(byte[] channel, byte[] frame) {
            this.channel = channel;
            this.frame = frame;
        }

        byte[] channel() {
            return channel;
        }

        byte[] frame() {
            return frame;
        }
    }

    /** Runs every {@code async} task on a real executor so the blocking subscribe loop runs off the test thread. */
    private record InlineAsyncScheduler(ExecutorService executor) implements Scheduler {
        @Override
        public void async(Runnable task) {
            executor.execute(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            executor.execute(task);
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
