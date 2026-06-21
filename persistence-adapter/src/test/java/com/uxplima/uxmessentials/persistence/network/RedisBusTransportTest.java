package com.uxplima.uxmessentials.persistence.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.network.BalanceChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Unit tests for {@link RedisBusTransport} without a real Redis: the {@link JedisPool} and {@link Jedis}
 * connections are Mockito mocks injected through the {@code openPool} seam, and the blocking
 * {@code jedis.subscribe} is faked to capture the {@link BinaryJedisPubSub} and park until {@code unsubscribe}
 * releases it (mirroring the real connection that blocks until the subscription ends). The classpath-guard
 * no-op cannot be exercised here because Jedis is on the test classpath; that branch is asserted by reading.
 */
class RedisBusTransportTest {

    private static final String CHANNEL = "uxmessentials:bus_v1";

    private final ExecutorService async = Executors.newCachedThreadPool();

    @AfterEach
    void shutdown() {
        async.shutdownNow();
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

    @Test
    void healthyReflectsTheSubscribeConnectionState() {
        FakeRedis redis = new FakeRedis();
        RedisBusTransport transport = transportOver(redis);

        assertThat(transport.healthy()).isFalse();

        transport.start(frame -> {});
        waitUntil(transport::healthy);

        transport.stop();
        redis.releaseSubscribe();
        waitUntil(() -> !transport.healthy());
    }

    @Test
    void aReceivedMessageIsHandedToOnFrameVerbatim() {
        FakeRedis redis = new FakeRedis();
        RedisBusTransport transport = transportOver(redis);
        CopyOnWriteArrayList<byte[]> received = new CopyOnWriteArrayList<>();

        transport.start(received::add);
        waitUntil(() -> redis.subscriber() != null);

        byte[] frame = NetworkMessageCodec.encode(new BalanceChanged("peer-2", UUID.randomUUID(), "coins"));
        redis.deliver(frame);

        assertThat(received).hasSize(1);
        assertThat(received.get(0)).isEqualTo(frame);
        // The same array reference is not required, only byte-for-byte equality; the frame must be unmodified.
        assertThat(NetworkMessageCodec.decode(received.get(0))).isInstanceOf(BalanceChanged.class);

        transport.stop();
        redis.releaseSubscribe();
    }

    @Test
    void sendPublishesTheExactFrameBytesOnTheConfiguredChannel() {
        FakeRedis redis = new FakeRedis();
        RedisBusTransport transport = transportOver(redis);
        transport.start(frame -> {});

        byte[] frame = NetworkMessageCodec.encode(new BalanceChanged("self-1", UUID.randomUUID(), "coins"));
        transport.send(frame);

        waitUntil(() -> !redis.published().isEmpty());
        assertThat(redis.published()).hasSize(1);
        Published only = redis.published().get(0);
        assertThat(only.channel()).isEqualTo(CHANNEL.getBytes(StandardCharsets.UTF_8));
        assertThat(only.frame()).isEqualTo(frame);

        transport.stop();
        redis.releaseSubscribe();
    }

    @Test
    void sendBeforeStartNeverTouchesRedis() {
        FakeRedis redis = new FakeRedis();
        RedisBusTransport transport = transportOver(redis);

        transport.send(new byte[] {1, 2, 3});

        assertThat(redis.published()).isEmpty();
        verify(redis.pool(), never()).getResource();
    }

    @Test
    void selectsTheConfiguredLogicalDatabaseOnPublish() {
        FakeRedis redis = new FakeRedis();
        RedisBusTransport transport = new RedisBusTransport(
                "localhost", 6379, "", 3, CHANNEL, new InlineAsyncScheduler(async), new NoopLogger(), redis::pool);
        transport.start(frame -> {});

        byte[] frame = NetworkMessageCodec.encode(new BalanceChanged("self-1", UUID.randomUUID(), "coins"));
        transport.send(frame);

        waitUntil(() -> !redis.published().isEmpty());
        // The fake pool hands the same jedis mock to both the subscribe loop and the publish, so select fires on
        // each borrowed connection; the contract is only that every borrowed connection targets the configured db.
        verify(redis.jedis(), atLeastOnce()).select(3);

        transport.stop();
        redis.releaseSubscribe();
    }

    @Test
    void stopIsIdempotentAndClosesThePool() {
        FakeRedis redis = new FakeRedis();
        RedisBusTransport transport = transportOver(redis);
        transport.start(frame -> {});
        waitUntil(() -> redis.subscriber() != null);

        transport.stop();
        redis.releaseSubscribe();
        transport.stop();

        verify(redis.pool(), atLeastOnce()).close();
        assertThat(transport.healthy()).isFalse();
    }

    private RedisBusTransport transportOver(FakeRedis redis) {
        return new RedisBusTransport(
                "localhost", 6379, "", 0, CHANNEL, new InlineAsyncScheduler(async), new NoopLogger(), redis::pool);
    }

    /**
     * A stand-in for the Redis server: a mock {@link JedisPool}/{@link Jedis} whose {@code subscribe} parks on
     * a latch (like the real blocking subscribe) until {@code unsubscribe} or {@code releaseSubscribe} frees
     * it, capturing the subscriber so the test can drive inbound messages, and whose {@code publish} records
     * every channel/frame pair.
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
                        // Block like a live subscribe until unsubscribe/release, mirroring the real connection.
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

        Jedis jedis() {
            return jedis;
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
