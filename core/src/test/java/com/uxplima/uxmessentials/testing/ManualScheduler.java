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
 * A scheduler a test drives by hand: async work queues until {@link #runAsync()}, so a test can look at the state
 * between the tick-thread part of a flow and its storage part. Thread hops run at once, and a player marked
 * {@link #leave gone} takes the retired branch, as Folia does for an entity that left.
 */
public final class ManualScheduler implements Scheduler {

    private final Deque<Runnable> async = new ArrayDeque<>();
    private final Set<UUID> gone = new HashSet<>();
    private boolean runningAsync;
    private boolean inline;

    /** From now on run async work at once, as a fast async lane would, instead of queueing it. */
    public void runAsyncInline() {
        inline = true;
    }

    /** Run every queued async task, including any a task queues while running. */
    public void runAsync() {
        Runnable next;
        while ((next = async.pollFirst()) != null) {
            runAsTask(next);
        }
    }

    /** Run every queued async task newest first, as when a later task overtakes an earlier, slower one. */
    public void runAsyncNewestFirst() {
        Runnable next;
        while ((next = async.pollLast()) != null) {
            runAsTask(next);
        }
    }

    private void runAsTask(Runnable task) {
        runningAsync = true;
        try {
            task.run();
        } finally {
            runningAsync = false;
        }
    }

    /** Whether the code calling this is running as an async task, so a fake port can tell which lane it is on. */
    public boolean isRunningAsync() {
        return runningAsync;
    }

    /** How many async tasks are waiting. */
    public int pendingAsync() {
        return async.size();
    }

    /** Treat {@code who} as having left: their entity hops take the retired branch from now on. */
    public void leave(PlayerRef who) {
        gone.add(who.uuid());
    }

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
        if (!gone.contains(player.uuid())) {
            onTick(task);
        }
    }

    @Override
    public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
        if (gone.contains(player.uuid())) {
            retired.run();
        } else {
            onTick(task);
        }
    }

    /** A hop back to a tick thread from inside an async task is no longer on the async lane. */
    private void onTick(Runnable task) {
        boolean wasAsync = runningAsync;
        runningAsync = false;
        try {
            task.run();
        } finally {
            runningAsync = wasAsync;
        }
    }

    @Override
    public void async(Runnable task) {
        if (inline) {
            runAsTask(task);
        } else {
            async.add(task);
        }
    }

    @Override
    public void asyncAfter(Duration delay, Runnable task) {
        async.add(task);
    }
}
