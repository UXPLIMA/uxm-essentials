package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.jspecify.annotations.NullMarked;

/**
 * One piece of inventory work handed to a player's own thread while the caller waits for it with a bound.
 *
 * <p>A wait that runs out cannot take back a task the scheduler already holds, and a credit that lands after the
 * caller was told "not paid" pays twice: a sale gives the items back and the coins arrive as well. So the waiter and
 * the task race for the work. The task runs it only if the waiter has not given up yet; the waiter gives up only if
 * the task has not started yet. Either the work never runs and the caller gets the fallback, or it runs and the
 * caller gets its own answer, however late.
 *
 * @param <T> what the work answers
 */
@NullMarked
final class EntityHandoff<T> {

    /** What happened when the work was waited for, so the caller can report a timeout or a failure. */
    interface Outcomes {

        void timedOut();

        void failed(Throwable failure);
    }

    private static final int WAITING = 0;
    private static final int RUNNING = 1;
    private static final int ABANDONED = 2;

    private final AtomicInteger state = new AtomicInteger(WAITING);
    private final CompletableFuture<T> result = new CompletableFuture<>();

    /** On the player's thread: run {@code work}, unless the waiter already gave up on it. */
    void run(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        if (!state.compareAndSet(WAITING, RUNNING)) {
            return;
        }
        try {
            result.complete(work.get());
        } catch (Throwable failure) {
            // Any throwable, an Error included, must settle the result: once the state is RUNNING the waiter no
            // longer gives up on its own and would otherwise wait forever. An Error still reaches the thread.
            result.completeExceptionally(failure);
            if (failure instanceof Error error) {
                throw error;
            }
        }
    }

    /** The player's thread will never run the work (the player left first): answer {@code fallback}. */
    void retire(T fallback) {
        if (state.compareAndSet(WAITING, ABANDONED)) {
            result.complete(fallback);
        }
    }

    /**
     * Wait up to {@code timeout} for the work. If it has not started by then it is abandoned, never runs, and
     * {@code fallback} is answered. If it has started, its own answer is waited for: its effect on the inventory
     * lands either way, and the caller must hear about it.
     */
    T await(Duration timeout, T fallback, Outcomes outcomes) {
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(outcomes, "outcomes");
        try {
            return result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutReached) {
            if (state.compareAndSet(WAITING, ABANDONED)) {
                outcomes.timedOut();
                return fallback;
            }
            return settled(fallback, outcomes);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (state.compareAndSet(WAITING, ABANDONED)) {
                return fallback;
            }
            return settled(fallback, outcomes);
        } catch (ExecutionException failure) {
            outcomes.failed(failure.getCause() == null ? failure : failure.getCause());
            return fallback;
        }
    }

    /** The answer of work that has already started; it is short inventory work, so this wait ends. */
    private T settled(T fallback, Outcomes outcomes) {
        try {
            return result.join();
        } catch (RuntimeException failure) {
            outcomes.failed(failure.getCause() == null ? failure : failure.getCause());
            return fallback;
        }
    }
}
