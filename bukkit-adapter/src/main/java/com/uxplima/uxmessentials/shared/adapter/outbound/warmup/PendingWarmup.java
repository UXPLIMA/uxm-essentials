package com.uxplima.uxmessentials.shared.adapter.outbound.warmup;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.application.port.Warmups.WarmupHandle;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * One in-flight warmup's handle. The owning context calls {@link #cancel()} from its move/damage
 * listener; the countdown calls {@link #complete()} on the player's region thread when it elapses.
 * Every transition, including {@link #supersede()} when the same player begins another warmup, is
 * guarded by a single {@link AtomicReference} state machine so a cancel and the warmup-fire can never both
 * run their callback, whichever {@code compareAndSet}s first wins, and the loser is a no-op.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. {@code state} is an {@link AtomicReference} mutated only by
 * {@code compareAndSet}; reads ({@link #isComplete()}, {@link #isCancelled()}) are safe from any
 * thread.
 */
@NullMarked
final class PendingWarmup implements WarmupHandle {

    private enum State {
        PENDING,
        COMPLETED,
        CANCELLED
    }

    private final Runnable onComplete;
    private final Runnable onCancel;
    private final Runnable onSuperseded;
    private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);

    PendingWarmup(PlayerRef who, Runnable onComplete, Runnable onCancel, Runnable onSuperseded) {
        Objects.requireNonNull(who, "who");
        this.onComplete = Objects.requireNonNull(onComplete, "onComplete");
        this.onCancel = Objects.requireNonNull(onCancel, "onCancel");
        this.onSuperseded = Objects.requireNonNull(onSuperseded, "onSuperseded");
    }

    /** Run the completion callback exactly once if the warmup has not been cancelled. */
    void complete() {
        if (state.compareAndSet(State.PENDING, State.COMPLETED)) {
            onComplete.run();
        }
    }

    @Override
    public void cancel() {
        if (state.compareAndSet(State.PENDING, State.CANCELLED)) {
            onCancel.run();
        }
    }

    @Override
    public void supersede() {
        // The same transition as a cancel, so a supersede racing a move or the countdown still lets only one of
        // the three callbacks run.
        if (state.compareAndSet(State.PENDING, State.CANCELLED)) {
            onSuperseded.run();
        }
    }

    @Override
    public boolean isComplete() {
        return state.get() == State.COMPLETED;
    }

    @Override
    public boolean isCancelled() {
        return state.get() == State.CANCELLED;
    }
}
