package com.uxplima.uxmessentials.testing;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Holds every task until {@link #runAll} is called, so a test decides when the off-tick work of one server happens
 * relative to another's. {@link #lastFirst} runs the newest task first, the way a thread pool may, and {@link #leave}
 * retires a player's entity so their tasks take the retired branch.
 */
public final class QueuedScheduler implements Scheduler {

    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private final Set<UUID> gone = new HashSet<>();
    private boolean lastFirst;

    /** Run the newest queued task first from now on. */
    public QueuedScheduler lastFirst() {
        lastFirst = true;
        return this;
    }

    /** Treat {@code player} as having left: their entity tasks no longer run, the retired branch does. */
    public void leave(PlayerRef player) {
        gone.add(player.uuid());
    }

    /** Run queued tasks, and the tasks they queue, until none are left. */
    public void runAll() {
        while (!tasks.isEmpty()) {
            (lastFirst ? tasks.pollLast() : tasks.pollFirst()).run();
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
        onEntity(player, task, () -> {});
    }

    @Override
    public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
        tasks.add(() -> (gone.contains(player.uuid()) ? retired : task).run());
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
