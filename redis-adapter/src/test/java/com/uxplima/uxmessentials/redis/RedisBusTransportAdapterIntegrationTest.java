package com.uxplima.uxmessentials.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.network.BalanceChanged;
import com.uxplima.uxmessentials.shared.network.BusTransport;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import io.lettuce.core.RedisURI;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Round-trip test for the Redis transport against a real Redis: a publishing {@link RedisBusTransportAdapter}
 * PUBLISHes an already-encoded frame on a channel, and a second subscribing adapter on the same channel hands
 * the verbatim bytes to its {@code onFrame} sink, which decode back equal to the original. This proves the seam
 * carries opaque bytes byte-identically over the live wire, not just over the fake-channel unit tests.
 *
 * <p>The broker is resolved in {@link #startRedis()} in priority order, so the test runs anywhere a real Redis
 * is available and skips cleanly (never fails) where none is: (1) an explicit {@code UXMESS_TEST_REDIS_URI} env
 * var / {@code uxmessentials.test.redis.uri} system property, (2) a reachable {@code redis://localhost:6379},
 * (3) a Testcontainers {@code redis:7-alpine} when Docker is available.
 */
@org.jspecify.annotations.NullUnmarked
class RedisBusTransportAdapterIntegrationTest {

    private static final int REDIS_PORT = 6379;

    private static @Nullable GenericContainer<?> container;
    private static @Nullable String redisHost;
    private static int redisPort;

    /** A distinct channel per run so a shared broker carries no cross-test state. */
    private final String channel = "uxmessentials:test:" + UUID.randomUUID();

    @BeforeAll
    static void startRedis() {
        String configured = configuredUri();
        if (configured != null && tcpReachable(configured)) {
            useUri(configured);
            return;
        }
        if (tcpReachable("redis://localhost:" + REDIS_PORT)) {
            useUri("redis://localhost:" + REDIS_PORT);
            return;
        }
        if (dockerAvailable()) {
            GenericContainer<?> started =
                    new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(REDIS_PORT);
            started.start();
            container = started;
            redisHost = started.getHost();
            redisPort = started.getMappedPort(REDIS_PORT);
            return;
        }
        Assumptions.assumeTrue(
                false, "no Redis reachable (set UXMESS_TEST_REDIS_URI, run redis on :6379, or enable Docker)");
    }

    @AfterAll
    static void stopRedis() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void aPublishedFrameReachesASecondSubscriberOverTheLiveWire() throws InterruptedException {
        BlockingQueue<byte[]> received = new ArrayBlockingQueue<>(4);
        BusTransport subscriber = RedisBusTransports.redis(
                redisHost, redisPort, "", 0, channel, new SynchronousScheduler(), new RecordingLogger());
        BusTransport publisher = RedisBusTransports.redis(
                redisHost, redisPort, "", 0, channel, new SynchronousScheduler(), new RecordingLogger());
        subscriber.start(received::add);
        publisher.start(frame -> {});

        NetworkMessage frame = new BalanceChanged("alpha", UUID.randomUUID(), "coins");
        byte[] encoded = NetworkMessageCodec.encode(frame);

        try {
            publisher.send(encoded);
            byte[] got = received.poll(10, TimeUnit.SECONDS);
            assertThat(got).isEqualTo(encoded);
            assertThat(NetworkMessageCodec.decode(got)).isEqualTo(frame);
        } finally {
            publisher.stop();
            subscriber.stop();
        }
    }

    private static void useUri(String uri) {
        RedisURI parsed = RedisURI.create(uri);
        redisHost = parsed.getHost();
        redisPort = parsed.getPort();
    }

    private static @Nullable String configuredUri() {
        String env = System.getenv("UXMESS_TEST_REDIS_URI");
        if (env != null && !env.isBlank()) {
            return env;
        }
        String prop = System.getProperty("uxmessentials.test.redis.uri");
        return prop != null && !prop.isBlank() ? prop : null;
    }

    private static boolean tcpReachable(String uri) {
        try {
            RedisURI parsed = RedisURI.create(uri);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(parsed.getHost(), parsed.getPort()), 1500);
                return true;
            }
        } catch (Exception unreachable) {
            return false;
        }
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable unavailable) {
            return false;
        }
    }

    /** Test {@link Scheduler} that runs every task inline on the calling thread. */
    private static final class SynchronousScheduler implements Scheduler {
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

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
