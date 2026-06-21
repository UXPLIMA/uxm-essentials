package com.uxplima.uxmessentials.persistence.network;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.BusTransport;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * The Redis pub/sub {@link BusTransport}: it carries the whole cross-server bus over a single Redis channel
 * with no Velocity proxy in the path. Where {@code PluginMessagingTransport} rides a carrier player's
 * connection through the proxy, this transport publishes each already-encoded {@code NetworkMessageCodec}
 * frame to a Redis channel and subscribes to that same channel, so every {@code NetworkMessage} type and
 * every {@code *Sync} context replicates straight between backends sharing a Redis server.
 *
 * <p>Frames are carried as opaque binary, not text: this uses Jedis's binary pub/sub
 * ({@link BinaryJedisPubSub} with {@code onMessage(byte[], byte[])} and {@code jedis.publish(byte[], byte[])})
 * so the exact frame bytes go on the wire with no base64 re-encode. The transport knows nothing about
 * {@code NetworkMessage} or the codec — the encode/decode, origin stamp, self-origin loop sentinel and
 * listener dispatch all live above this seam in {@code BusCore}; only the byte-moving machinery is here.
 *
 * <p>The self-origin echo is the core's concern, not this transport's: Redis pub/sub does not deliver a
 * publisher's own message back on the very connection that published it, but {@code BusCore} still drops any
 * frame whose origin equals this backend's {@code server-id}, which is the correct guard when two backends
 * share Redis state. This transport adds no second de-duplication mechanism.
 *
 * <p>Connection lifecycle mirrors {@code RedisWalletSync}: publishes borrow a connection from a pooled
 * {@link JedisPool} ({@code try (Jedis j = pool.getResource())}); the blocking subscribe loop owns one
 * dedicated connection and runs on the injected {@link Scheduler#async} executor, never a hand-rolled
 * {@code Thread}, reconnecting after a 5s backoff when the connection drops. If the Jedis library is absent
 * from the runtime classpath the transport disables itself with a single WARN and becomes a no-op rather than
 * crashing the bus. Publish failures log at most one WARN per 60s so a Redis outage cannot flood the log.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>volatile-field</b> for the running flag, the pool, the live subscription and the connected
 * state, all written by the start/stop calls and the subscribe loop and read by {@link #healthy} and
 * {@link #send}. The subscribe loop is the only owner of the blocking {@code jedis.subscribe} call; it runs on
 * the async executor and never touches a Bukkit API, so it never blocks a tick or region thread.
 */
@NullMarked
public final class RedisBusTransport implements BusTransport {

    private static final long RECONNECT_BACKOFF_MS = 5_000L;
    private static final long PUBLISH_WARN_INTERVAL_MS = 60_000L;

    private final String host;
    private final int port;
    private final String password;
    private final byte[] channel;
    private final Scheduler scheduler;
    private final Logger log;
    private final Supplier<JedisPool> poolFactory;

    private volatile boolean running;
    private volatile boolean connected;
    private volatile @Nullable JedisPool pool;
    private volatile @Nullable BinaryJedisPubSub subscription;
    private volatile @Nullable Consumer<byte[]> onFrame;
    private final AtomicLong lastPublishWarnAt = new AtomicLong(0L);

    public RedisBusTransport(String host, int port, String password, String channel, Scheduler scheduler, Logger log) {
        this(host, port, password, channel, scheduler, log, null);
    }

    /**
     * Test seam: a caller-supplied {@code poolFactory} substitutes the {@link JedisPool} the loop borrows
     * from, so a unit test can inject a mock pool without a live Redis. A {@code null} factory means production
     * — open a real pool against the configured host/port. Package-private so only the same-package test names
     * a Jedis type; production wiring goes through the public constructor and the {@link NetworkTransports}
     * factory.
     */
    RedisBusTransport(
            String host,
            int port,
            String password,
            String channel,
            Scheduler scheduler,
            Logger log,
            @Nullable Supplier<JedisPool> poolFactory) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.password = Objects.requireNonNull(password, "password");
        this.channel = Objects.requireNonNull(channel, "channel").getBytes(StandardCharsets.UTF_8);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.poolFactory = poolFactory != null ? poolFactory : () -> new JedisPool(this.host, this.port);
    }

    /**
     * Open the pool and arm the binary subscribe loop on the async executor, routing every received frame's
     * bytes to {@code onFrame}. No-op if Jedis is unavailable or the transport is already running.
     */
    @Override
    public void start(Consumer<byte[]> onFrame) {
        this.onFrame = Objects.requireNonNull(onFrame, "onFrame");
        if (running) {
            return;
        }
        if (!jedisOnClasspath()) {
            log.warn("redis bus transport disabled: the Jedis library is not on the runtime classpath");
            return;
        }
        JedisPool opened;
        try {
            opened = poolFactory.get();
        } catch (RuntimeException cause) {
            log.error("redis bus transport failed to open connection pool to " + host + ":" + port, cause);
            return;
        }
        pool = opened;
        running = true;
        scheduler.async(this::subscribeLoop);
    }

    /**
     * Publish one already-encoded frame to the Redis channel. Borrows a pooled connection, publishes the
     * verbatim frame bytes, and logs a rate-limited WARN on failure. Fire-and-forget and a no-op when the
     * transport is not running, so a send before start never touches Redis.
     */
    @Override
    public void send(byte[] frame) {
        Objects.requireNonNull(frame, "frame");
        if (!running) {
            return;
        }
        byte[] copy = frame.clone();
        scheduler.async(() -> publishNow(copy));
    }

    /** Unsubscribe, close the pool and stop the loop. Idempotent; safe to call when start was a no-op. */
    @Override
    public void stop() {
        running = false;
        connected = false;
        BinaryJedisPubSub sub = subscription;
        subscription = null;
        if (sub != null) {
            try {
                sub.unsubscribe();
            } catch (RuntimeException cause) {
                log.warn("redis bus transport failed to unsubscribe on stop: {}", cause);
            }
        }
        JedisPool current = pool;
        pool = null;
        if (current != null) {
            try {
                current.close();
            } catch (RuntimeException cause) {
                log.warn("redis bus transport failed to close pool on stop: {}", cause);
            }
        }
    }

    /** True while the subscribe connection is live; flips false on a dropped connection and on stop. */
    @Override
    public boolean healthy() {
        return running && connected;
    }

    private void publishNow(byte[] frame) {
        JedisPool current = pool;
        if (current == null) {
            return;
        }
        try (Jedis jedis = current.getResource()) {
            authenticate(jedis);
            jedis.publish(channel, frame);
        } catch (RuntimeException cause) {
            warnRateLimited("redis bus transport failed to publish a frame", cause);
        }
    }

    private void subscribeLoop() {
        while (running) {
            JedisPool current = pool;
            if (current == null) {
                return;
            }
            try (Jedis jedis = current.getResource()) {
                authenticate(jedis);
                BinaryJedisPubSub sub = frameSubscriber();
                subscription = sub;
                connected = true;
                // Blocks until the subscription ends (unsubscribe on stop, or the connection drops).
                jedis.subscribe(sub, channel);
            } catch (RuntimeException cause) {
                if (running) {
                    log.warn("redis bus transport subscription dropped, reconnecting: {}", cause);
                }
            } finally {
                connected = false;
            }
            if (running && !sleepBackoff()) {
                return;
            }
        }
    }

    private BinaryJedisPubSub frameSubscriber() {
        return new BinaryJedisPubSub() {
            @Override
            public void onMessage(byte[] inboundChannel, byte[] message) {
                Consumer<byte[]> sink = onFrame;
                if (sink != null) {
                    sink.accept(message);
                }
            }
        };
    }

    private void authenticate(Jedis jedis) {
        if (!password.isEmpty()) {
            jedis.auth(password);
        }
    }

    private boolean sleepBackoff() {
        try {
            Thread.sleep(RECONNECT_BACKOFF_MS);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void warnRateLimited(String message, RuntimeException cause) {
        long now = System.currentTimeMillis();
        long previous = lastPublishWarnAt.get();
        if (now - previous >= PUBLISH_WARN_INTERVAL_MS && lastPublishWarnAt.compareAndSet(previous, now)) {
            log.warn("{}: {}", message, cause);
        }
    }

    private static boolean jedisOnClasspath() {
        try {
            Class.forName("redis.clients.jedis.Jedis");
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }
}
