package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link Scheduler} decorator that counts outstanding {@link #async(Runnable)} work so the worlds
 * wiring can drain it on module stop/reload. Only the async executor is tracked: it is where the
 * worlds use cases run their off-tick write tails (file delete, metadata save/delete), and the one
 * place an in-flight task can outlive a disable. Every other method delegates verbatim, including the
 * defaulted overloads, so the decorator is otherwise transparent to the inner scheduler.
 */
@NullMarked
public final class InFlightScheduler implements Scheduler {

    private final Scheduler inner;
    private final AtomicInteger inFlight;

    public InFlightScheduler(Scheduler inner, AtomicInteger inFlight) {
        this.inner = Objects.requireNonNull(inner, "inner");
        this.inFlight = Objects.requireNonNull(inFlight, "inFlight");
    }

    @Override
    public void onGlobal(Runnable task) {
        inner.onGlobal(task);
    }

    @Override
    public void onRegion(Position position, Runnable task) {
        inner.onRegion(position, task);
    }

    @Override
    public void onEntity(PlayerRef player, Runnable task) {
        inner.onEntity(player, task);
    }

    @Override
    public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
        inner.onEntity(player, task, retired);
    }

    @Override
    public boolean onGlobalThread() {
        return inner.onGlobalThread();
    }

    @Override
    public boolean ownsEntity(PlayerRef player) {
        return inner.ownsEntity(player);
    }

    @Override
    public void async(Runnable task) {
        Objects.requireNonNull(task, "task");
        inFlight.incrementAndGet();
        inner.async(() -> {
            try {
                task.run();
            } finally {
                inFlight.decrementAndGet();
            }
        });
    }

    @Override
    public void asyncAfter(Duration delay, Runnable task) {
        inner.asyncAfter(delay, task);
    }

    @Override
    public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
        return inner.repeatGlobal(task, initialDelay, period);
    }
}
