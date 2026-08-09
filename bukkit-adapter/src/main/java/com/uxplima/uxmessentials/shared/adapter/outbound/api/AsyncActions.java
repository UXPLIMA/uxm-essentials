package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Runs a published action off the calling thread and hands the caller a future for it.
 *
 * <p>The same move {@link AsyncQueries} makes for reads, named separately because a write is a different promise:
 * by the time the future completes the change has happened and its event has been published.
 *
 * <p>A use case that throws completes the future exceptionally. That is deliberate: a refusal the server modelled
 * ("they cannot afford it") comes back as a failure value, so an exception here means something broke rather than
 * something was denied, and the two should not arrive by the same route.
 */
@NullMarked
public final class AsyncActions {

    private AsyncActions() {}

    /** Run {@code write} on a worker thread and complete the returned future with its result. */
    public static <T> CompletableFuture<T> perform(Scheduler scheduler, Supplier<T> write) {
        return AsyncQueries.supply(scheduler, write);
    }

    /**
     * Run {@code write} on the server's own thread and complete the returned future with its result.
     *
     * <p>For a write that reaches past the database into the running server: disconnecting a player, telling
     * everybody what happened. A worker thread may not do either, and the caller still gets a future rather than
     * having to know which thread it was on when it asked.
     */
    public static <T> CompletableFuture<T> onServer(Scheduler scheduler, Supplier<T> write) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(write, "write");
        CompletableFuture<T> answer = new CompletableFuture<>();
        scheduler.onGlobal(() -> {
            try {
                answer.complete(write.get());
            } catch (RuntimeException failure) {
                answer.completeExceptionally(failure);
            }
        });
        return answer;
    }

    /**
     * Run {@code write} on the thread that owns {@code who} and complete the returned future with its result.
     *
     * <p>For a write that touches the live player: their inventory, their flight, their position. On Folia that is
     * the only thread allowed to, and on Paper it is the tick thread either way.
     *
     * <p>The retired path matters here in a way it does not for a read. A player who leaves between the call and
     * the hop would otherwise leave the future hanging forever, and a consumer chaining off it would simply stop.
     */
    public static <T> CompletableFuture<T> onPlayer(Scheduler scheduler, PlayerRef who, Supplier<T> write, T gone) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(write, "write");
        Objects.requireNonNull(gone, "gone");
        CompletableFuture<T> answer = new CompletableFuture<>();
        scheduler.onEntity(
                who,
                () -> {
                    try {
                        answer.complete(write.get());
                    } catch (RuntimeException failure) {
                        answer.completeExceptionally(failure);
                    }
                },
                () -> answer.complete(gone));
        return answer;
    }
}
