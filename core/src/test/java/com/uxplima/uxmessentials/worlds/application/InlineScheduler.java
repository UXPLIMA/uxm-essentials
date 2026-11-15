package com.uxplima.uxmessentials.worlds.application;

import java.time.Duration;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/** Runs every task at once on the calling thread, so a use case's off-tick tail has finished when it returns. */
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
