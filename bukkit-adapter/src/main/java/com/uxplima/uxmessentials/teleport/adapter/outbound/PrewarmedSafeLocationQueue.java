package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
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
 * <p>The urgent path ({@link #urgentSearch(WorldRef)}) serves the queue first and, only when empty, runs a
 * bounded off-thread search via {@link SafeSearchValidator}, blocking that worker (never the tick thread)
 * for a short timeout. Durable restart-survival of the queue (the {@code rtp_queue} mirror, ADR 0010 §7)
 * is the persistence-adapter's concern and is a documented stub here — this queue is in-memory and
 * cold-starts empty, warming on the first refill.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. {@code ready} holds a {@link ConcurrentLinkedQueue} per world;
 * {@code refilling} holds one {@link AtomicBoolean} per world so at most one refill task is outstanding.
 * The {@code running} flag is observed by the refill loop, which exits when the module stops.
 */
@NullMarked
public final class PrewarmedSafeLocationQueue implements SafeLocationQueue {

    private static final Duration URGENT_TIMEOUT = Duration.ofMillis(1500);

    private final Scheduler scheduler;
    private final SafeSearchValidator validator;
    private final RtpWorldSettings settings;
    private final Logger log;
    private final BooleanSupplier running;
    private final ConcurrentHashMap<UUID, ConcurrentLinkedQueue<RtpSafeLocation>> ready = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AtomicBoolean> refilling = new ConcurrentHashMap<>();

    public PrewarmedSafeLocationQueue(
            Scheduler scheduler,
            SafeSearchValidator validator,
            RtpWorldSettings settings,
            Logger log,
            BooleanSupplier running) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.log = Objects.requireNonNull(log, "log");
        this.running = Objects.requireNonNull(running, "running");
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
        if (queued.isPresent()) {
            return queued;
        }
        return area(world).flatMap(this::boundedSearch);
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
        if (queue.size() >= settings.lowWaterMark()) {
            return;
        }
        AtomicBoolean guard = refilling.computeIfAbsent(world.uid(), id -> new AtomicBoolean());
        if (guard.compareAndSet(false, true)) {
            scheduler.async(() -> runRefill(world, queue, guard));
        }
    }

    private void runRefill(WorldRef world, ConcurrentLinkedQueue<RtpSafeLocation> queue, AtomicBoolean guard) {
        try {
            int attempts = 0;
            while (running.getAsBoolean()
                    && queue.size() < settings.targetSize()
                    && attempts < settings.attemptBudget()) {
                attempts++;
                Optional<SafeSearchArea> area = area(world);
                if (area.isEmpty()) {
                    return;
                }
                awaitCandidate(area.get()).ifPresent(queue::offer);
            }
            if (queue.isEmpty()) {
                log.debug("rtp refill produced no location for world {} in {} attempts", world.name(), attempts);
            }
        } finally {
            guard.set(false);
        }
    }

    private Optional<RtpSafeLocation> boundedSearch(SafeSearchArea area) {
        for (int attempt = 0; attempt < settings.attemptBudget(); attempt++) {
            Optional<RtpSafeLocation> found = awaitCandidate(area);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private Optional<RtpSafeLocation> awaitCandidate(SafeSearchArea area) {
        try {
            return validator.tryOne(area).get(URGENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (TimeoutException timedOut) {
            return Optional.empty();
        } catch (java.util.concurrent.ExecutionException failed) {
            log.warn(
                    "rtp candidate validation failed for world {}: {}",
                    area.world().name(),
                    String.valueOf(failed.getMessage()));
            return Optional.empty();
        }
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
        double max = Math.min(settings.maxRadius(), borderRadius);
        if (max < settings.minRadius()) {
            return Optional.empty();
        }
        return Optional.of(new SafeSearchArea(
                worldRef, centerX, centerZ, settings.minRadius(), settings.maxRadius(), borderRadius));
    }

    /** Drop every queue and refill guard on module stop. */
    public void clear() {
        ready.clear();
        refilling.clear();
    }
}
