package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
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

    /**
     * Run {@code read} on the thread that owns {@code who} and complete the returned future with its result.
     *
     * <p>The third and last kind of published read: one that looks at a live player rather than at a row. An
     * inventory is only readable from the thread that owns the entity, which on Folia is a region thread and not
     * the global one, so neither of the two above will do.
     *
     * <p>A player who leaves between the call and the hop is answered with {@code gone} rather than left hanging,
     * the same way a write to a departed player is.
     */
    public static <T> CompletableFuture<T> onPlayer(Scheduler scheduler, PlayerRef who, Supplier<T> read, T gone) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(read, "read");
        Objects.requireNonNull(gone, "gone");
        CompletableFuture<T> answer = new CompletableFuture<>();
        scheduler.onEntity(
                who,
                () -> {
                    try {
                        answer.complete(read.get());
                    } catch (RuntimeException failure) {
                        answer.completeExceptionally(failure);
                    }
                },
                () -> answer.complete(gone));
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
