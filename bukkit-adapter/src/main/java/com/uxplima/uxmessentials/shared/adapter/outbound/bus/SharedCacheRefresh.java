package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Reloads the caches a peer can change when this backend may have missed the frames that would have kept them
 * current.
 *
 * <p>The proxy relays a frame to a backend only through a player online there and keeps no queue, so every frame
 * sent while a backend sat empty is lost, and a cache that is only ever refreshed by a frame would stay stale until
 * the next restart. Two triggers close that gap:
 *
 * <ul>
 *   <li>The first player to join an empty backend is the moment it becomes reachable again. That join drops the
 *       per-player caches on the joining thread, before the join handlers warm the joiner, and reloads the set
 *       caches on the async lane.
 *   <li>A bounded timer ({@code network.cache-refresh-seconds}) reloads the set caches. Per-player caches already
 *       expire on their own short timer, so the safety timer leaves them alone rather than lining every expiry up
 *       into one burst of reloads.
 * </ul>
 *
 * <p>Redis delivers to an empty backend, so a Redis-only network skips the join trigger and keeps only the timer. The
 * player count is kept here rather than read from the server, because two quits on two Folia region threads would
 * each still see the other player online and the backend would never count as empty. Reloads coalesce: a trigger
 * that lands while one runs queues a single follow-up instead of doubling every query. A backend with
 * {@code network.enabled = false} has no peers and no frames, so nothing here ever runs for it.
 */
@NullMarked
public final class SharedCacheRefresh {

    private final RemoteSyncRegistry registry;
    private final Scheduler scheduler;
    private final Logger log;
    private final boolean refreshOnReachable;
    private final Duration every;
    private final AtomicInteger online;
    private final AtomicBoolean reloading = new AtomicBoolean();
    private final AtomicBoolean requested = new AtomicBoolean();
    private volatile boolean running;

    private SharedCacheRefresh(
            RemoteSyncRegistry registry,
            Scheduler scheduler,
            Logger log,
            boolean refreshOnReachable,
            Duration every,
            int onlineNow) {
        this.registry = registry;
        this.scheduler = scheduler;
        this.log = log;
        this.refreshOnReachable = refreshOnReachable;
        this.every = every;
        this.online = new AtomicInteger(Math.max(0, onlineNow));
    }

    /**
     * The refresh for this backend's {@code network} block, over the resyncs on {@code registry}. {@code onlineNow}
     * is how many players are online as it is built, so an enable with players already on does not count their next
     * join as a backend coming back.
     */
    public static SharedCacheRefresh wire(
            NetworkConfig network, RemoteSyncRegistry registry, Scheduler scheduler, Logger log, int onlineNow) {
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(log, "log");
        if (!network.enabled()) {
            return new SharedCacheRefresh(registry, scheduler, log, false, Duration.ZERO, onlineNow);
        }
        return new SharedCacheRefresh(
                registry, scheduler, log, network.relaysThroughProxy(), network.cacheRefreshInterval(), onlineNow);
    }

    /**
     * A player joined. When the backend was empty it is reachable again: the per-player caches drop here, on the
     * caller's thread, and the set caches reload on the async lane.
     */
    public void playerJoined() {
        if (online.incrementAndGet() == 1 && refreshOnReachable) {
            registry.dropPlayerCaches(this::reportFailure);
            requestReload();
        }
    }

    /** A player left; the last one leaving makes the next join count as the backend coming back. */
    public void playerLeft() {
        online.updateAndGet(count -> Math.max(0, count - 1));
    }

    /** Start the safety timer; the first reload comes one period after the start. A no-op when the timer is off. */
    public void start() {
        if (every.isZero()) {
            return;
        }
        running = true;
        scheduleNext();
    }

    /** Stop the safety timer; the in-flight loop drops out on its next tick. Idempotent. */
    public void stop() {
        running = false;
    }

    private void scheduleNext() {
        if (!running) {
            return;
        }
        scheduler.asyncAfter(every, this::tick);
    }

    private void tick() {
        if (!running) {
            return;
        }
        requestReload();
        scheduleNext();
    }

    private void requestReload() {
        requested.set(true);
        if (reloading.compareAndSet(false, true)) {
            scheduler.async(this::drain);
        }
    }

    private void drain() {
        try {
            while (requested.getAndSet(false)) {
                registry.resync(this::reportFailure);
            }
        } finally {
            reloading.set(false);
        }
        // A request that arrived between the last check and the flag clearing would otherwise wait for the next one.
        if (requested.get() && reloading.compareAndSet(false, true)) {
            scheduler.async(this::drain);
        }
    }

    private void reportFailure(RuntimeException failure) {
        log.error("a shared cache could not be reloaded from the database", failure);
    }
}
