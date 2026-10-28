package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.jspecify.annotations.NullMarked;

/**
 * An action that is asked for often and run at most once per wait: a request while a run is already waiting joins
 * it instead of queueing another.
 *
 * <p>A hologram is redrawn for every viewer in range at once, and each viewer whose own text is resolved asks for a
 * redraw. Without joining, a board seen by a hundred players queued a hundred redraws of a hundred viewers each on
 * every refresh, so its cost grew with the square of the crowd. Joined, the requests made before the redraw runs
 * are served by that one redraw, which reads every viewer's latest text.
 */
@NullMarked
final class QueuedOnce {

    private final AtomicBoolean waiting = new AtomicBoolean();
    private final Consumer<Runnable> submit;
    private final Runnable action;

    /** {@code submit} hands a run to the thread it belongs on; {@code action} is what the run does. */
    QueuedOnce(Consumer<Runnable> submit, Runnable action) {
        this.submit = Objects.requireNonNull(submit, "submit");
        this.action = Objects.requireNonNull(action, "action");
    }

    /** Run the action soon, unless a run is already waiting to start. */
    void request() {
        if (!waiting.compareAndSet(false, true)) {
            return;
        }
        submit.accept(() -> {
            // Cleared before the action runs, so a request made during it is served by a run of its own.
            waiting.set(false);
            action.run();
        });
    }
}
