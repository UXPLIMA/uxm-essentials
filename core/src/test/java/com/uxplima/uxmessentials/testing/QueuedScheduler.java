package com.uxplima.uxmessentials.testing;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Holds every task until {@link #runAll} is called, so a test decides when the off-tick work of one server happens
 * relative to another's.
 */
public final class QueuedScheduler implements Scheduler {

    private final Deque<Runnable> tasks = new ArrayDeque<>();

    /** Run queued tasks, and the tasks they queue, until none are left. */
    public void runAll() {
        while (!tasks.isEmpty()) {
            tasks.poll().run();
        }
    }

    @Override
    public void onGlobal(Runnable task) {
        tasks.add(task);
    }

    @Override
    public void onRegion(Position position, Runnable task) {
        tasks.add(task);
    }

    @Override
    public void onEntity(PlayerRef player, Runnable task) {
        tasks.add(task);
    }

    @Override
    public void async(Runnable task) {
        tasks.add(task);
    }

    @Override
    public void asyncAfter(Duration delay, Runnable task) {
        tasks.add(task);
    }
}
