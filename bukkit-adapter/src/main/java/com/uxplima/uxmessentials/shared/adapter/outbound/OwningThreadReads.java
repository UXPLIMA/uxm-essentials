package com.uxplima.uxmessentials.shared.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * Answers a question about the server on the thread that owns what it reads, for an adapter whose port is asked from
 * any thread.
 *
 * <p>Use cases that read the database run on the async lane and still ask what a player may do or what a block is,
 * through ports whose answer is read from the Bukkit API. On the async lane such a read is not allowed, so the read
 * is sent to the owner (the player's entity thread, or the region thread of a position) and the async caller waits
 * for its answer. The wait is bounded: a server that stalls longer, or an owner that is gone (the player left, the
 * world unloaded), gets the caller's {@code fallback}, which is the answer an offline player gets anyway.
 *
 * <p>A caller already on a tick thread reads in place. On Paper that thread owns everything; on Folia it may not own
 * what it reads, but a tick thread must never block on another region, which could be waiting on it.
 */
@NullMarked
public final class OwningThreadReads {

    /** Several ticks of a lagging server, far beyond a healthy hop, which lands on the next tick. */
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    private final Scheduler scheduler;
    private final BooleanSupplier onTickThread;
    private final Duration timeout;
    private final Logger log;
    private final AtomicBoolean stallReported = new AtomicBoolean();

    public OwningThreadReads(Scheduler scheduler, BooleanSupplier onTickThread, Duration timeout, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.onTickThread = Objects.requireNonNull(onTickThread, "onTickThread");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Reads on the server's own tick threads, waiting up to the default bound for a hop. */
    public static OwningThreadReads onServer(Scheduler scheduler, BooleanSupplier onTickThread, Logger log) {
        return new OwningThreadReads(scheduler, onTickThread, DEFAULT_TIMEOUT, log);
    }

    /** {@code read} on {@code player}'s entity thread, or {@code fallback} when it cannot run there in time. */
    public <T> T forPlayer(PlayerRef player, Supplier<T> read, T fallback) {
        Objects.requireNonNull(player, "player");
        return answer(read, fallback, (task, retired) -> scheduler.onEntity(player, task, retired));
    }

    /** {@code read} on the region thread owning {@code at}, or {@code fallback} when it cannot run there in time. */
    public <T> T at(Position at, Supplier<T> read, T fallback) {
        Objects.requireNonNull(at, "at");
        return answer(read, fallback, (task, retired) -> scheduler.onRegion(at, task, retired));
    }

    private <T> T answer(Supplier<T> read, T fallback, Hop hop) {
        Objects.requireNonNull(read, "read");
        Objects.requireNonNull(fallback, "fallback");
        if (onTickThread.getAsBoolean()) {
            return read.get();
        }
        CompletableFuture<T> answer = new CompletableFuture<>();
        try {
            hop.schedule(() -> settle(answer, read), () -> answer.complete(fallback));
        } catch (RuntimeException refused) {
            return fallback;
        }
        return await(answer, fallback);
    }

    private static <T> void settle(CompletableFuture<T> answer, Supplier<T> read) {
        try {
            answer.complete(read.get());
        } catch (RuntimeException failure) {
            answer.completeExceptionally(failure);
        }
    }

    private <T> T await(CompletableFuture<T> answer, T fallback) {
        try {
            return answer.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException stalled) {
            // Settled with the fallback so a read that lands later has nobody left to answer.
            answer.complete(fallback);
            if (stallReported.compareAndSet(false, true)) {
                log.warn("event=owning_thread_read_timeout timeout_ms={}", String.valueOf(timeout.toMillis()));
            }
            return fallback;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (ExecutionException failed) {
            // The read threw on the owning thread; the caller hears it as if it had read in place.
            if (failed.getCause() instanceof RuntimeException failure) {
                throw failure;
            }
            throw new IllegalStateException("owning thread read failed", failed.getCause());
        }
    }

    /** One way to reach the owning thread: run {@code task} there, or {@code retired} when it never can. */
    @FunctionalInterface
    private interface Hop {
        void schedule(Runnable task, Runnable retired);
    }
}
