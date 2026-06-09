package com.uxplima.uxmessentials.persistence.warps;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

/**
 * Cross-server cache invalidation for warps over a Redis pub/sub channel, the warps analogue of the in-house
 * bus's {@code WarpSync}. A local {@code /setwarp} / {@code /delwarp} / move publishes {@code invalidate:<name>}
 * on the shared channel and every backend listening drops its cached warp set so the next {@code /warp} or
 * {@code /warps} reloads the authoritative rows from the shared database.
 *
 * <p>Invalidation messages only — no lock primitive lives here; warps carry no money authority. Publishes
 * borrow a connection from a pooled {@link JedisPool}; the blocking subscribe loop owns one dedicated
 * connection and runs on the injected {@link Scheduler#async} executor rather than a hand-rolled thread. If
 * the Jedis library is absent at runtime the feature disables itself with a single WARN.
 */
@NullMarked
public final class RedisWarpSync {

    private static final String INVALIDATE_PREFIX = "invalidate:";
    private static final long RECONNECT_BACKOFF_MS = 5_000L;
    private static final long PUBLISH_WARN_INTERVAL_MS = 60_000L;

    private final CachedWarpRepository cache;
    private final String host;
    private final int port;
    private final String password;
    private final String channel;
    private final Scheduler scheduler;
    private final Logger log;

    private volatile boolean running;
    private volatile @Nullable JedisPool pool;
    private volatile @Nullable JedisPubSub subscription;
    private final AtomicLong lastPublishWarnAt = new AtomicLong(0L);

    public RedisWarpSync(
            CachedWarpRepository cache,
            String host,
            int port,
            String password,
            String channel,
            Scheduler scheduler,
            Logger log) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.password = Objects.requireNonNull(password, "password");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Open the pool and arm the subscribe loop on the async executor. No-op if Jedis is unavailable. */
    public void start() {
        if (running) {
            return;
        }
        if (!jedisOnClasspath()) {
            log.warn("redis warp sync disabled: the Jedis library is not on the runtime classpath");
            return;
        }
        try {
            pool = new JedisPool(host, port);
        } catch (RuntimeException cause) {
            log.error("redis warp sync failed to open connection pool to " + host + ":" + port, cause);
            return;
        }
        running = true;
        scheduler.async(this::subscribeLoop);
    }

    /** Broadcast that {@code warpName} changed so peers drop their cached warp set. Off-tick. */
    public void publish(String warpName) {
        Objects.requireNonNull(warpName, "warpName");
        if (!running) {
            return;
        }
        scheduler.async(() -> publishNow(warpName));
    }

    private void publishNow(String warpName) {
        JedisPool current = pool;
        if (current == null) {
            return;
        }
        try (Jedis jedis = current.getResource()) {
            authenticate(jedis);
            jedis.publish(channel, INVALIDATE_PREFIX + warpName);
        } catch (RuntimeException cause) {
            warnRateLimited("redis warp sync failed to publish invalidate for " + warpName, cause);
        }
    }

    /** Tear down the subscription and pool. Safe to call when start was a no-op. */
    public void stop() {
        running = false;
        JedisPubSub sub = subscription;
        if (sub != null) {
            try {
                sub.unsubscribe();
            } catch (RuntimeException cause) {
                log.warn("redis warp sync failed to unsubscribe on stop: {}", cause);
            }
        }
        JedisPool current = poolForClose();
        if (current != null) {
            try {
                current.close();
            } catch (RuntimeException cause) {
                log.warn("redis warp sync failed to close pool on stop: {}", cause);
            }
        }
    }

    private @Nullable JedisPool poolForClose() {
        JedisPool current = pool;
        pool = null;
        return current;
    }

    private void subscribeLoop() {
        while (running) {
            JedisPool current = pool;
            if (current == null) {
                return;
            }
            try (Jedis jedis = current.getResource()) {
                authenticate(jedis);
                JedisPubSub sub = invalidationSubscriber();
                subscription = sub;
                jedis.subscribe(sub, channel);
            } catch (RuntimeException cause) {
                if (running) {
                    log.warn("redis warp sync subscription dropped, reconnecting: {}", cause);
                    if (!sleepBackoff()) {
                        return;
                    }
                }
            }
        }
    }

    private JedisPubSub invalidationSubscriber() {
        return new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                if (message.startsWith(INVALIDATE_PREFIX)) {
                    cache.invalidateAll();
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
