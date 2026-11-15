package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Reloads every cache a peer can change (the resyncs registered on the {@link RemoteSyncRegistry}) when this
 * backend may have missed the frames that would have kept them current.
 *
 * <p>The proxy relays a frame to a backend only through a player online there and keeps no queue, so every frame
 * sent while a backend sat empty is lost, and a cache that is only ever refreshed by a frame would stay stale until
 * the next restart. Two triggers close that gap. The first player to join an empty backend is the moment it becomes
 * reachable again, so that join reloads everything once. A bounded timer ({@code network.cache-refresh-seconds})
 * catches anything else a transport lost. Redis delivers to an empty backend, so a Redis-only network skips the join
 * trigger and keeps only the timer.
 *
 * <p>Every reload runs on the async lane: the resyncs read the shared database. The timer is the self-rescheduling
 * {@link Scheduler#asyncAfter} loop {@link ClusterHeartbeat} uses, so {@link #stop()} leaves no task behind. A
 * backend with {@code network.enabled = false} has no peers and no bus frames, so it builds an instance that does
 * nothing.
 */
@NullMarked
public final class SharedCacheRefresh {

    private final RemoteSyncRegistry registry;
    private final Scheduler scheduler;
    private final Logger log;
    private final boolean refreshOnReachable;
    private final Duration every;
    private final AtomicBoolean empty;
    private volatile boolean running;

    private SharedCacheRefresh(
            RemoteSyncRegistry registry,
            Scheduler scheduler,
            Logger log,
            boolean refreshOnReachable,
            Duration every,
            boolean emptyNow) {
        this.registry = registry;
        this.scheduler = scheduler;
        this.log = log;
        this.refreshOnReachable = refreshOnReachable;
        this.every = every;
        this.empty = new AtomicBoolean(emptyNow);
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
            return new SharedCacheRefresh(registry, scheduler, log, false, Duration.ZERO, onlineNow <= 0);
        }
        return new SharedCacheRefresh(
                registry, scheduler, log, network.relaysThroughProxy(), network.cacheRefreshInterval(), onlineNow <= 0);
    }

    /** A player joined; when the backend was empty, it is reachable again and the shared caches reload. */
    public void playerJoined() {
        if (refreshOnReachable && empty.compareAndSet(true, false)) {
            scheduler.async(this::refreshNow);
        }
    }

    /** A player left and {@code stillOnline} remain; at zero the next join is the backend coming back. */
    public void playerLeft(int stillOnline) {
        if (stillOnline <= 0) {
            empty.set(true);
        }
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
        refreshNow();
        scheduleNext();
    }

    private void refreshNow() {
        registry.resync(failure -> log.error("a shared cache could not be reloaded from the database", failure));
    }
}
