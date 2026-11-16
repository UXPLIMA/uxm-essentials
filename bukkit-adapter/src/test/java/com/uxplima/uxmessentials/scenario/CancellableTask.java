package com.uxplima.uxmessentials.scenario;

import java.util.function.Consumer;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import org.jspecify.annotations.Nullable;

/** A region-scheduler task backed by a MockBukkit task, so the plugin can cancel what it scheduled. */
final class CancellableTask implements ScheduledTask {

    private final Plugin plugin;
    private final boolean repeating;
    private @Nullable BukkitTask handle;
    private volatile ExecutionState state = ExecutionState.IDLE;

    CancellableTask(Plugin plugin, boolean repeating) {
        this.plugin = plugin;
        this.repeating = repeating;
    }

    void bind(BukkitTask handle) {
        this.handle = handle;
    }

    Runnable body(Consumer<ScheduledTask> task) {
        return () -> {
            if (state == ExecutionState.CANCELLED) {
                return;
            }
            state = ExecutionState.RUNNING;
            task.accept(this);
            if (state == ExecutionState.RUNNING) {
                state = repeating ? ExecutionState.IDLE : ExecutionState.FINISHED;
            }
        };
    }

    @Override
    public Plugin getOwningPlugin() {
        return plugin;
    }

    @Override
    public boolean isRepeatingTask() {
        return repeating;
    }

    @Override
    public CancelledState cancel() {
        if (state == ExecutionState.CANCELLED) {
            return CancelledState.CANCELLED_ALREADY;
        }
        if (state == ExecutionState.FINISHED) {
            return CancelledState.ALREADY_EXECUTED;
        }
        boolean running = state == ExecutionState.RUNNING;
        state = ExecutionState.CANCELLED;
        BukkitTask bound = handle;
        if (bound != null) {
            bound.cancel();
        }
        return running ? CancelledState.NEXT_RUNS_CANCELLED : CancelledState.CANCELLED_BY_CALLER;
    }

    @Override
    public ExecutionState getExecutionState() {
        return state;
    }
}
