package com.uxplima.uxmessentials.teleport.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;

/**
 * Outbound port over the per-world pre-warmed random-teleport queue (ADR 0010). A {@code /rtp} is served
 * O(1) by {@link #poll(WorldRef)}; the queue refills asynchronously below a threshold and is DB-backed
 * so it survives a restart. Two serving paths share the queue: the background path ({@code /rtp}) and
 * the urgent path (respawn / first-join), which serves from the queue and, when it is momentarily drained,
 * kicks a non-blocking refill and reports empty rather than blocking on an inline search.
 *
 * <p>The application asks for a location and triggers a refill; the adapter owns the concurrency (the
 * {@code ConcurrentLinkedQueue}, the per-world in-flight guard, and the budget-bounded, tick-sliced
 * off-thread search against {@link com.uxplima.uxmessentials.teleport.domain.SafeSearchPolicy}). No path
 * blocks a thread for the duration of a search; {@link #requestRefill(WorldRef)} is fire-and-forget.
 */
public interface SafeLocationQueue {

    /**
     * Take and remove the head safe location for {@code world}, revalidating it cheaply on serve
     * (stale-radius discard). Empty when the queue is momentarily drained.
     */
    Optional<RtpSafeLocation> poll(WorldRef world);

    /**
     * The urgent serving path: a location for {@code world} from the pre-warmed queue, kicking a
     * non-blocking refill and reporting empty when the queue is momentarily drained. It never blocks the
     * caller on an inline search; a drained queue means the caller shows the clear no-location message and
     * the queue warms for the next attempt.
     */
    Optional<RtpSafeLocation> urgentSearch(WorldRef world);

    /** Whether {@code world} has an RTP queue at all (a hub world may have none). */
    boolean hasQueue(WorldRef world);

    /**
     * Fire one async refill for {@code world} if it is below its low-water mark, deduped per world so
     * concurrent {@code /rtp}s never launch N refills. Fire-and-forget: the caller does not wait.
     */
    void requestRefill(WorldRef world);
}
