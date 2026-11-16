package com.uxplima.uxmessentials.scenario;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import net.kyori.adventure.text.Component;

import org.jspecify.annotations.Nullable;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.inventory.InventoryMock;
import org.mockbukkit.mockbukkit.scheduler.BukkitSchedulerMock;

/**
 * MockBukkit's server with a global and an async scheduler a scenario can drive to a quiet point.
 *
 * <p>The stock ones hand out tasks that abort on cancel, and the plugin cancels every repeating task it started when it
 * disables. The stock async lane can only be waited on by cancelling every repeating task and ticking until nothing is
 * scheduled, which never ends for a plugin whose timers reschedule themselves. Here async work runs on a pool this
 * server counts, and a delayed or repeating async task is timed by ticks and handed to that pool when it is due.
 *
 * <p>It also has a world folder, which the worlds module lists on start, and chests whose holder can be read without
 * a snapshot, which the plugin asks of every window it sees open; the stock server leaves both unimplemented.
 */
// ServerMock's raw getBanList override is inherited, not written here, and javac reports it on every subclass.
@SuppressWarnings("unchecked")
final class ScenarioServer extends ServerMock {

    private static final long MILLIS_PER_TICK = 50L;
    private static final String ASYNC_THREAD = "scenario-async";

    private final GlobalRegionScheduler global = new Global();
    private final AsyncScheduler async = new Async();
    private final ExecutorService asyncPool = Executors.newCachedThreadPool(work -> {
        Thread thread = new Thread(work, ASYNC_THREAD);
        thread.setDaemon(true);
        return thread;
    });
    private final Path worldContainer = temporaryDirectory();
    private final List<Throwable> asyncFailures = new CopyOnWriteArrayList<>();
    private final Object idle = new Object();
    private int asyncInFlight;

    @Override
    public File getWorldContainer() {
        return worldContainer.toFile();
    }

    @Override
    public Path getLevelDirectory() {
        return worldContainer.resolve("world");
    }

    @Override
    public InventoryMock createInventory(@Nullable InventoryHolder owner, int size) {
        return chest(owner, size);
    }

    @Override
    public InventoryMock createInventory(@Nullable InventoryHolder owner, int size, Component title) {
        return chest(owner, size);
    }

    @Override
    public InventoryMock createInventory(@Nullable InventoryHolder owner, InventoryType type, Component title) {
        return type == InventoryType.CHEST
                ? chest(owner, type.getDefaultSize())
                : super.createInventory(owner, type, title);
    }

    private static InventoryMock chest(@Nullable InventoryHolder owner, int size) {
        if (size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalArgumentException("a chest holds 9 to 54 slots in rows of 9, not " + size);
        }
        return new ScenarioChest(owner, size);
    }

    /** What async work threw; on a real server it would reach the console, here it has to reach the scenario. */
    List<Throwable> asyncFailures() {
        return List.copyOf(asyncFailures);
    }

    @Override
    public GlobalRegionScheduler getGlobalRegionScheduler() {
        return global;
    }

    @Override
    public AsyncScheduler getAsyncScheduler() {
        return async;
    }

    /**
     * Runs at least one game second of ticks and keeps ticking until no async work is running, the way a server keeps
     * ticking while its async lane works: async work often waits for a hop onto a player's thread, which only a tick
     * runs. Between ticks it waits for the async lane, briefly, so work that does not wait on a tick is not raced.
     */
    void settle() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int ticks = 0;
        while (ticks < 20 || asyncBusy()) {
            getScheduler().performOneTick();
            ticks++;
            awaitAsyncIdle(TimeUnit.MILLISECONDS.toNanos(5));
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("async work still running after 10 seconds:" + asyncStacks());
            }
        }
    }

    private boolean asyncBusy() {
        synchronized (idle) {
            return asyncInFlight > 0;
        }
    }

    /** Waits up to this long for the async lane to go idle. */
    private void awaitAsyncIdle(long nanos) {
        long deadline = System.nanoTime() + nanos;
        synchronized (idle) {
            while (asyncInFlight > 0) {
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    return;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(idle, left);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while waiting for async work", interrupted);
                }
            }
        }
    }

    private static String asyncStacks() {
        StringBuilder stacks = new StringBuilder();
        Thread.getAllStackTraces().forEach((thread, frames) -> {
            if (thread.getName().equals(ASYNC_THREAD)) {
                stacks.append(System.lineSeparator()).append(thread.getName());
                for (StackTraceElement frame : frames) {
                    stacks.append(System.lineSeparator()).append("    at ").append(frame);
                }
            }
        });
        return stacks.toString();
    }

    void shutdownAsync() {
        asyncPool.shutdownNow();
    }

    private static Path temporaryDirectory() {
        try {
            return Files.createTempDirectory("scenario-worlds");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private BukkitSchedulerMock ticks() {
        return getScheduler();
    }

    private Runnable offTick(Runnable work) {
        return () -> {
            synchronized (idle) {
                asyncInFlight++;
            }
            try {
                asyncPool.execute(() -> {
                    try {
                        work.run();
                    } catch (RuntimeException | Error failure) {
                        asyncFailures.add(failure);
                    } finally {
                        finished();
                    }
                });
            } catch (RuntimeException rejected) {
                finished();
                throw rejected;
            }
        };
    }

    private void finished() {
        synchronized (idle) {
            asyncInFlight--;
            idle.notifyAll();
        }
    }

    private static CancellableTask bound(CancellableTask task, BukkitTask handle) {
        task.bind(handle);
        return task;
    }

    private static long toTicks(long amount, TimeUnit unit) {
        return Math.max(1L, unit.toMillis(amount) / MILLIS_PER_TICK);
    }

    private final class Global implements GlobalRegionScheduler {

        @Override
        public void execute(Plugin plugin, Runnable run) {
            ticks().runTask(plugin, run);
        }

        @Override
        public ScheduledTask run(Plugin plugin, Consumer<ScheduledTask> task) {
            CancellableTask scheduled = new CancellableTask(plugin, false);
            return bound(scheduled, ticks().runTask(plugin, scheduled.body(task)));
        }

        @Override
        public ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, long delayTicks) {
            CancellableTask scheduled = new CancellableTask(plugin, false);
            return bound(scheduled, ticks().runTaskLater(plugin, scheduled.body(task), delayTicks));
        }

        @Override
        public ScheduledTask runAtFixedRate(
                Plugin plugin, Consumer<ScheduledTask> task, long initialDelayTicks, long periodTicks) {
            CancellableTask scheduled = new CancellableTask(plugin, true);
            return bound(scheduled, ticks().runTaskTimer(plugin, scheduled.body(task), initialDelayTicks, periodTicks));
        }

        @Override
        public void cancelTasks(Plugin plugin) {
            ticks().cancelTasks(plugin);
        }
    }

    private final class Async implements AsyncScheduler {

        @Override
        public ScheduledTask runNow(Plugin plugin, Consumer<ScheduledTask> task) {
            CancellableTask scheduled = new CancellableTask(plugin, false);
            offTick(scheduled.body(task)).run();
            return scheduled;
        }

        @Override
        public ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, long delay, TimeUnit unit) {
            CancellableTask scheduled = new CancellableTask(plugin, false);
            return bound(scheduled, ticks().runTaskLater(plugin, offTick(scheduled.body(task)), toTicks(delay, unit)));
        }

        @Override
        public ScheduledTask runAtFixedRate(
                Plugin plugin, Consumer<ScheduledTask> task, long initialDelay, long period, TimeUnit unit) {
            CancellableTask scheduled = new CancellableTask(plugin, true);
            return bound(
                    scheduled,
                    ticks().runTaskTimer(
                                    plugin,
                                    offTick(scheduled.body(task)),
                                    toTicks(initialDelay, unit),
                                    toTicks(period, unit)));
        }

        @Override
        public void cancelTasks(Plugin plugin) {
            ticks().cancelTasks(plugin);
        }
    }
}
