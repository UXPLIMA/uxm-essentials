package com.uxplima.uxmessentials.warps.application;

import java.time.Duration;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * A {@link Scheduler} test double that runs every task inline on the calling thread, including
 * {@code async}. It lets the {@link UseWarp} use-case tests observe the visitor-counter write (now
 * dispatched through {@code scheduler.async}) synchronously, exactly as before the write moved off the
 * region thread.
 */
final class InlineScheduler implements Scheduler {

    @Override
    public void onGlobal(Runnable task) {
        task.run();
    }

    @Override
    public void onRegion(Position position, Runnable task) {
        task.run();
    }

    @Override
    public void onEntity(PlayerRef player, Runnable task) {
        task.run();
    }

    @Override
    public void async(Runnable task) {
        task.run();
    }

    @Override
    public void asyncAfter(Duration delay, Runnable task) {
        task.run();
    }
}
