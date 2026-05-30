package com.uxplima.uxmessentials.shared.application.port;

import java.time.Duration;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Folia-aware scheduling port. Application and use-case code schedule every piece of work through
 * this contract; the adapter dispatches each call to the right Folia scheduler
 * ({@code GlobalRegionScheduler} / {@code RegionScheduler} / {@code EntityScheduler} /
 * {@code AsyncScheduler}). The legacy {@code BukkitScheduler} is never used — that assumption (one
 * main thread owns every entity and world) is exactly what Folia breaks.
 *
 * <p>Pick the most specific method: {@link #onEntity} for per-player work, {@link #onRegion} for
 * work bound to a block or location (chunk load for an RTP candidate, a setwarp block check),
 * {@link #onGlobal} only for genuinely global state ({@code /time}, {@code /weather}, a broadcast) —
 * it serialises onto one thread and kills Folia's parallelism. Plan each flow to hop once, do the
 * work, and finish; do not ping-pong between schedulers.
 *
 * <p>The port is deliberately fire-and-forget — every method returns {@code void} and there is no
 * cancellation handle. Production found callers either need no cancellation or model it in their own
 * state (the teleport context's warmup re-checks a {@code cancelled} flag each tick rather than
 * holding a handle), and the fire-and-forget shape is what lets a {@code FeatureModule} drain its own
 * in-flight work on {@code stop()} with no global scheduler holding orphaned tasks. A module that
 * needs to bound its outstanding work tracks it itself (a {@code Phaser} or an {@code AtomicInteger}).
 * If a future feature genuinely needs cancellation or repeating semantics, extend this port rather
 * than reaching for a raw Paper scheduler.
 */
public interface Scheduler {

    /** Run on the global region thread — global game state only; serialises, so use sparingly. */
    void onGlobal(Runnable task);

    /** Run on the region thread owning {@code position} — block, chunk, and location work. */
    void onRegion(Position position, Runnable task);

    /**
     * Run on the region thread owning {@code player}'s entity. Silently no-ops when the player is
     * offline (the Folia entity scheduler refuses a despawned entity); callers needing to wait for a
     * relog listen for the join instead.
     */
    void onEntity(PlayerRef player, Runnable task);

    /** Run off any tick thread. The task must not touch the Bukkit API. */
    void async(Runnable task);

    /** Run off any tick thread after {@code delay}, on a non-shared executor. */
    void asyncAfter(Duration delay, Runnable task);
}
