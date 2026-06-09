package com.uxplima.uxmessentials.persistence.economy;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

/**
 * Cross-server cache invalidation for wallet balances over a Redis pub/sub channel. This is the second
 * representative cross-server seam alongside the in-house bus's {@code WalletSync}: when a peer commits a
 * balance change it publishes {@code invalidate:<owner>} on the shared channel, and every backend listening
 * drops that owner from its {@link CachedWalletRepository} so the next {@code /balance} reloads the
 * authoritative figure from the shared database.
 *
 * <p>This carries <strong>invalidation messages only</strong> — it never holds money authority and never
 * implements a distributed lock. Money safety is the database's guarded {@code UPDATE … WHERE amount >= ?}
 * (see {@link JooqWalletRepository}); a single-instance Redis "Redlock" would be both unsafe and redundant,
 * so no lock primitive lives here.
 *
 * <p>Connection lifecycle: publishes borrow a connection from a pooled {@link JedisPool}
 * ({@code try (Jedis j = pool.getResource())}); the blocking subscribe loop owns one dedicated connection and
 * runs on the injected {@link Scheduler#async} executor, never a hand-rolled {@code Thread}. If the Jedis
 * library is absent from the runtime classpath the feature disables itself with a single WARN rather than
 * crashing the module.
 */
@NullMarked
public final class RedisWalletSync {

    private static final String INVALIDATE_PREFIX = "invalidate:";
    private static final long RECONNECT_BACKOFF_MS = 5_000L;
    private static final long PUBLISH_WARN_INTERVAL_MS = 60_000L;

    private final CachedWalletRepository cache;
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

    public RedisWalletSync(
            CachedWalletRepository cache,
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
            log.warn("redis wallet sync disabled: the Jedis library is not on the runtime classpath");
            return;
        }
        try {
            pool = new JedisPool(host, port);
        } catch (RuntimeException cause) {
            log.error("redis wallet sync failed to open connection pool to " + host + ":" + port, cause);
            return;
        }
        running = true;
        scheduler.async(this::subscribeLoop);
    }

    /** Broadcast that {@code owner}'s balance changed so peers drop their cached snapshot. Off-tick. */
    public void publish(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        if (!running) {
            return;
        }
        scheduler.async(() -> publishNow(owner));
    }

    private void publishNow(UUID owner) {
        JedisPool current = pool;
        if (current == null) {
            return;
        }
        try (Jedis jedis = current.getResource()) {
            authenticate(jedis);
            jedis.publish(channel, INVALIDATE_PREFIX + owner);
        } catch (RuntimeException cause) {
            warnRateLimited("redis wallet sync failed to publish invalidate for " + owner, cause);
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
                log.warn("redis wallet sync failed to unsubscribe on stop: {}", cause);
            }
        }
        JedisPool current = poolForClose();
        if (current != null) {
            try {
                current.close();
            } catch (RuntimeException cause) {
                log.warn("redis wallet sync failed to close pool on stop: {}", cause);
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
                    log.warn("redis wallet sync subscription dropped, reconnecting: {}", cause);
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
                if (!message.startsWith(INVALIDATE_PREFIX)) {
                    return;
                }
                String raw = message.substring(INVALIDATE_PREFIX.length());
                try {
                    cache.invalidateOwner(UUID.fromString(raw));
                } catch (IllegalArgumentException malformed) {
                    log.warn("redis wallet sync ignored malformed invalidate payload: {}", raw);
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
