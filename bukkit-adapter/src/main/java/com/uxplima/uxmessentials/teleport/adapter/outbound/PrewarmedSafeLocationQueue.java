package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.BudgetedSafeSearch;
import com.uxplima.uxmessentials.teleport.application.port.SafeLocationQueue;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;
import org.jspecify.annotations.NullMarked;

/**
 * The pre-warmed per-world random-teleport queue (ADR 0010). A {@code /rtp} is served O(1) by
 * {@link #poll(WorldRef)} off a per-world {@link ConcurrentLinkedQueue}; the queue refills asynchronously
 * through the injected {@link Scheduler} when it drops below its low-water mark, deduped per world by a
 * {@code compareAndSet}-guarded flag so concurrent {@code /rtp}s never launch N refills. A polled location
 * that no longer fits the world's current border/radius is discarded on serve and the next is polled.
 *
 * <p>The refill launches {@link BudgetedSafeSearch}es one at a time until the queue reaches its target size
 * or a per-cycle cap is hit — <strong>none of which blocks a thread</strong>. Each search chains its own
 * attempts through the scheduler (budget-bounded and tick-sliced) and completes a future; the refill composes
 * onto it with {@code thenAccept} rather than parking a worker on a {@code .get()} the way the pre-P1 loop did.
 * Durable restart-survival of the queue (the {@code rtp_queue} mirror, ADR 0010 §7) is the persistence-adapter's
 * concern and is a documented stub here — this queue is in-memory and cold-starts empty, warming on the first
 * refill.
 *
 * <p>The urgent path ({@link #urgentSearch(WorldRef)}) serves the queue and, when it is momentarily drained,
 * kicks a non-blocking refill and reports empty rather than blocking the caller on an inline search. Routing
 * respawn / first-join RTP fully through the async engine is a later phase; here the caller shows the clear
 * no-location message and the queue warms for the next attempt.
 *
 * <p>The active tuning sits behind an {@link AtomicReference} so {@code /settpr} can reset the search radii at
 * runtime ({@link #updateRadii}); the swap is whole-record, and the queue is dropped on swap so refills
 * re-validate against the new zone rather than serving stale candidates.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. {@code ready} holds a {@link ConcurrentLinkedQueue} per world;
 * {@code refilling} holds one {@link AtomicBoolean} per world so at most one refill cycle is outstanding.
 * {@code settings} is an {@link AtomicReference} read lock-free on every serve/refill and replaced whole by
 * {@code /settpr}. The {@code running} flag is observed by the refill chain, which exits when the module stops.
 */
@NullMarked
public final class PrewarmedSafeLocationQueue implements SafeLocationQueue {

    private final Scheduler scheduler;
    private final BudgetedSafeSearch search;
    private final AtomicReference<RtpWorldSettings> settings;
    private final Logger log;
    private final BooleanSupplier running;
    private final ConcurrentHashMap<UUID, ConcurrentLinkedQueue<RtpSafeLocation>> ready = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AtomicBoolean> refilling = new ConcurrentHashMap<>();

    public PrewarmedSafeLocationQueue(
            Scheduler scheduler,
            BudgetedSafeSearch search,
            RtpWorldSettings settings,
            Logger log,
            BooleanSupplier running) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.search = Objects.requireNonNull(search, "search");
        this.settings = new AtomicReference<>(Objects.requireNonNull(settings, "settings"));
        this.log = Objects.requireNonNull(log, "log");
        this.running = Objects.requireNonNull(running, "running");
    }

    /**
     * Swap the live search radii used by {@code /rtp} (the {@code /settpr} runtime path), keeping the queue
     * tuning untouched, then drop every pre-warmed location so refills re-validate against the new zone rather
     * than serving candidates from the old one. The swap is a single {@link AtomicReference#set} so an in-flight
     * {@code /rtp} reads either the whole previous tuning or the whole new one.
     */
    public RtpWorldSettings updateRadii(double minRadius, double maxRadius) {
        RtpWorldSettings updated = settings().withRadii(minRadius, maxRadius);
        settings.set(updated);
        clear();
        return updated;
    }

    private RtpWorldSettings settings() {
        // The reference is seeded non-null at construction and only ever replaced with a non-null value, so the
        // get is never null; the requireNonNull makes that contract explicit for the null checker.
        return Objects.requireNonNull(settings.get(), "settings");
    }

    @Override
    public Optional<RtpSafeLocation> poll(WorldRef world) {
        Objects.requireNonNull(world, "world");
        ConcurrentLinkedQueue<RtpSafeLocation> queue = ready.get(world.uid());
        if (queue == null) {
            return Optional.empty();
        }
        return area(world).flatMap(a -> pollWithin(queue, a));
    }

    @Override
    public Optional<RtpSafeLocation> urgentSearch(WorldRef world) {
        Objects.requireNonNull(world, "world");
        Optional<RtpSafeLocation> queued = poll(world);
        if (queued.isEmpty()) {
            // No inline search on the caller's thread — warm the queue and let the caller show the no-location
            // message; the budgeted search runs off-thread for the next attempt.
            requestRefill(world);
        }
        return queued;
    }

    @Override
    public boolean hasQueue(WorldRef world) {
        Objects.requireNonNull(world, "world");
        return area(world).isPresent();
    }

    @Override
    public void requestRefill(WorldRef world) {
        Objects.requireNonNull(world, "world");
        if (!running.getAsBoolean()) {
            return;
        }
        ConcurrentLinkedQueue<RtpSafeLocation> queue =
                ready.computeIfAbsent(world.uid(), id -> new ConcurrentLinkedQueue<>());
        if (queue.size() >= settings().lowWaterMark()) {
            return;
        }
        AtomicBoolean guard = refilling.computeIfAbsent(world.uid(), id -> new AtomicBoolean());
        if (guard.compareAndSet(false, true)) {
            scheduler.async(() -> launchSearch(world, queue, guard, 0));
        }
    }

    private void launchSearch(
            WorldRef world, ConcurrentLinkedQueue<RtpSafeLocation> queue, AtomicBoolean guard, int launched) {
        RtpWorldSettings current = settings();
        Optional<SafeSearchArea> area = area(world);
        if (!running.getAsBoolean()
                || queue.size() >= current.targetSize()
                || launched >= current.targetSize()
                || area.isEmpty()) {
            finishRefill(world, queue, guard, launched);
            return;
        }
        try {
            var ignored = search.search(area.get(), current.searchBudget())
                    .thenAccept(found -> afterSearch(world, queue, guard, launched, found));
        } catch (RuntimeException failure) {
            log.warn(
                    "rtp refill search failed to start for world {}: {}",
                    world.name(),
                    String.valueOf(failure.getMessage()));
            guard.set(false);
        }
    }

    private void afterSearch(
            WorldRef world,
            ConcurrentLinkedQueue<RtpSafeLocation> queue,
            AtomicBoolean guard,
            int launched,
            Optional<RtpSafeLocation> found) {
        found.ifPresent(queue::offer);
        launchSearch(world, queue, guard, launched + 1);
    }

    private void finishRefill(
            WorldRef world, ConcurrentLinkedQueue<RtpSafeLocation> queue, AtomicBoolean guard, int launched) {
        if (queue.isEmpty() && launched > 0) {
            log.debug("rtp refill produced no location for world {} after {} searches", world.name(), launched);
        }
        guard.set(false);
    }

    private static Optional<RtpSafeLocation> pollWithin(
            ConcurrentLinkedQueue<RtpSafeLocation> queue, SafeSearchArea area) {
        RtpSafeLocation candidate;
        while ((candidate = queue.poll()) != null) {
            if (candidate.stillWithin(area)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private Optional<SafeSearchArea> area(WorldRef worldRef) {
        World world = Bukkit.getWorld(worldRef.uid());
        if (world == null) {
            return Optional.empty();
        }
        WorldBorder border = world.getWorldBorder();
        double borderRadius = border.getSize() / 2.0;
        double centerX = border.getCenter().getX();
        double centerZ = border.getCenter().getZ();
        RtpWorldSettings current = settings();
        double max = Math.min(current.maxRadius(), borderRadius);
        if (max < current.minRadius()) {
            return Optional.empty();
        }
        return Optional.of(
                new SafeSearchArea(worldRef, centerX, centerZ, current.minRadius(), current.maxRadius(), borderRadius));
    }

    /** Drop every queue and refill guard on module stop. */
    public void clear() {
        ready.clear();
        refilling.clear();
    }
}
