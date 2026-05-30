package com.uxplima.uxmessentials.teleport.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;

/**
 * Outbound port over the per-world pre-warmed random-teleport queue (ADR 0010). A {@code /rtp} is served
 * O(1) by {@link #poll(WorldRef)}; the queue refills asynchronously below a threshold and is DB-backed
 * so it survives a restart. Two serving paths share the queue: the background path ({@code /rtp}) and
 * the urgent path (respawn / first-join), the latter falling back to a bounded off-thread search only
 * when the queue is empty.
 *
 * <p>The application asks for a location and triggers a refill; the adapter owns the concurrency (the
 * {@code ConcurrentLinkedQueue}, the per-world in-flight {@code AtomicInteger}, the off-thread
 * validation against {@link com.uxplima.uxmessentials.teleport.domain.SafeSearchPolicy}). The
 * fire-and-forget {@link #requestRefill(WorldRef)} never blocks the requester.
 */
public interface SafeLocationQueue {

    /**
     * Take and remove the head safe location for {@code world}, revalidating it cheaply on serve
     * (stale-radius discard). Empty when the queue is momentarily drained — the urgent path then runs a
     * bounded search via {@link #urgentSearch(WorldRef)}.
     */
    Optional<RtpSafeLocation> poll(WorldRef world);

    /**
     * The urgent serving path: a location for {@code world} now, falling back to a bounded off-thread
     * search when the queue is empty (still never on the tick thread). Used by respawn and first-join
     * RTP, which cannot wait for an async refill.
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
