package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Runs a published query off the calling thread and hands the caller a future for it.
 *
 * <p>Every repository in this plugin is synchronous and blocking, because that is the honest shape of a database
 * call. A consumer asking us a question is almost always on a tick thread, so the read has to move: one place does
 * that move, rather than each query implementation growing its own copy of the same four lines.
 *
 * <p>Through the {@link Scheduler} port rather than {@code CompletableFuture.supplyAsync}, which is forbidden here
 * and guarded: the common pool is not ours to fill with database work, and on Folia the scheduler is the only thing
 * that knows what a worker thread is.
 *
 * <p>A query that throws completes the future exceptionally rather than dying silently on a worker thread. The
 * consumer sees it through {@code exceptionally} or {@code whenComplete}, which is where they can do something
 * about it.
 */
@NullMarked
public final class AsyncQueries {

    private AsyncQueries() {}

    /**
     * Run {@code read} on the server's own thread and complete the returned future with its result.
     *
     * <p>For the few reads that are not database reads: live server state a worker thread may not touch. The
     * caller still gets a future, so it need not know which of the two kinds it asked for.
     */
    public static <T> CompletableFuture<T> onServer(Scheduler scheduler, Supplier<T> read) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(read, "read");
        CompletableFuture<T> answer = new CompletableFuture<>();
        scheduler.onGlobal(() -> {
            try {
                answer.complete(read.get());
            } catch (RuntimeException failure) {
                answer.completeExceptionally(failure);
            }
        });
        return answer;
    }

    /** Run {@code read} on a worker thread and complete the returned future with its result. */
    public static <T> CompletableFuture<T> supply(Scheduler scheduler, Supplier<T> read) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(read, "read");
        CompletableFuture<T> answer = new CompletableFuture<>();
        scheduler.async(() -> {
            try {
                answer.complete(read.get());
            } catch (RuntimeException failure) {
                answer.completeExceptionally(failure);
            }
        });
        return answer;
    }
}
