package com.uxplima.uxmessentials.testing;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.port.Warmups.CancelCause;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Warmups that end only when the test says: {@link #finish} counts one down, {@link #move} cancels it as a step off
 * the origin block would, {@link #supersede} stops it as the player's next teleport would. A player has one warmup at a time, and starting another supersedes the earlier one, as the
 * plugin's tracking warmups do. Each handle lets exactly one of its complete, cancel and supersede callbacks run.
 */
public final class ManualWarmups implements Warmups {

    private final Map<UUID, Pending> current = new HashMap<>();

    @Override
    public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
        return begin(who, kind, onComplete, onCancel, onCancel);
    }

    @Override
    public WarmupHandle begin(
            PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel, Runnable onSuperseded) {
        return begin(who, kind, onComplete, (CancelCause cause) -> onCancel.run(), onSuperseded);
    }

    @Override
    public WarmupHandle begin(
            PlayerRef who,
            WarmupKind kind,
            Runnable onComplete,
            Consumer<CancelCause> onCancel,
            Runnable onSuperseded) {
        Pending started = new Pending(onComplete, onCancel, onSuperseded);
        Pending earlier = current.put(who.uuid(), started);
        if (earlier != null) {
            earlier.supersede();
        }
        return started;
    }

    /** True while {@code who} is standing out a warmup. */
    public boolean waiting(PlayerRef who) {
        return current.containsKey(who.uuid());
    }

    /** Let {@code who}'s warmup count down to its end. */
    public void finish(PlayerRef who) {
        Pending due = current.remove(who.uuid());
        if (due != null) {
            due.complete();
        }
    }

    /** Cancel {@code who}'s warmup as a step off the origin block does. */
    public void move(PlayerRef who) {
        Pending due = current.remove(who.uuid());
        if (due != null) {
            due.cancel(CancelCause.MOVED);
        }
    }

    /** Stop {@code who}'s warmup as their next teleport does, here or to another backend. */
    public void supersede(PlayerRef who) {
        Pending due = current.remove(who.uuid());
        if (due != null) {
            due.supersede();
        }
    }

    /** The handle of {@code who}'s running warmup, for a test that races its end against a cancel. */
    public WarmupHandle handleOf(PlayerRef who) {
        return Objects.requireNonNull(current.get(who.uuid()), "no warmup running");
    }

    /** One warmup, ended once by whichever of its three ways gets there first. */
    public static final class Pending implements WarmupHandle {

        private enum State {
            PENDING,
            COMPLETED,
            CANCELLED
        }

        private final Runnable onComplete;
        private final Consumer<CancelCause> onCancel;
        private final Runnable onSuperseded;
        private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);

        Pending(Runnable onComplete, Consumer<CancelCause> onCancel, Runnable onSuperseded) {
            this.onComplete = onComplete;
            this.onCancel = onCancel;
            this.onSuperseded = onSuperseded;
        }

        /** Count the warmup down, unless something already ended it. */
        public void complete() {
            if (state.compareAndSet(State.PENDING, State.COMPLETED)) {
                onComplete.run();
            }
        }

        @Override
        public void cancel() {
            cancel(CancelCause.ABORTED);
        }

        @Override
        public void cancel(CancelCause cause) {
            if (state.compareAndSet(State.PENDING, State.CANCELLED)) {
                onCancel.accept(cause);
            }
        }

        @Override
        public void supersede() {
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
}
