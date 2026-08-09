package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
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
}
