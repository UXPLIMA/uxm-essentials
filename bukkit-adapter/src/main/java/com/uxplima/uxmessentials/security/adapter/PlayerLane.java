package com.uxplima.uxmessentials.security.adapter;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Runs one player's session writes off the tick, one after another in the order they were handed in: a proof recorded
 * as they verify lands before the release as they leave, so a release can never land first and leave the session
 * held without an end. Different players' work runs side by side.
 */
@NullMarked
final class PlayerLane {

    private static final CompletableFuture<Void> IDLE = CompletableFuture.completedFuture(null);

    private final Scheduler scheduler;
    private final Logger log;
    private final Map<UUID, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();

    PlayerLane(Scheduler scheduler, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Run {@code work} for {@code player} on the async lane once everything handed in for them before has run. A
     * failure is logged as {@code event} and does not stop the work after it.
     */
    void run(UUID player, String event, Runnable work) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(work, "work");
        Runnable guarded = () -> {
            try {
                work.run();
            } catch (RuntimeException failure) {
                log.error("event=" + event + " player=" + player, failure);
            }
        };
        CompletableFuture<Void> next = tails.compute(
                player,
                (id, tail) -> (tail == null ? IDLE : tail)
                        .handle((done, failure) -> (Void) null)
                        .thenRunAsync(guarded, this::submit));
        var unused = next.whenComplete((done, failure) -> tails.remove(player, next));
    }

    /** Hand one step to the async lane; one the scheduler refuses while the plugin stops is dropped. */
    private void submit(Runnable step) {
        try {
            scheduler.async(step);
        } catch (RuntimeException refused) {
            log.warn("event=security_session_write_dropped reason=scheduler_stopped");
        }
    }
}
