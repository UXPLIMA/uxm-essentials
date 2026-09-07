package com.uxplima.uxmessentials.shared.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.jspecify.annotations.NullMarked;

/**
 * This plugin's {@link Scheduler} port seen as the one uxmLib's menu engine takes.
 *
 * <p>The engine reaches for five of the twelve answers a {@link com.uxplima.uxmlib.scheduler.Scheduler} offers:
 * the viewer's entity thread, the global thread, an async hop, a delayed async hop, and one repeating global
 * timer for a refreshing menu. Each of those has an exact counterpart on this side, so the adapter forwards and
 * decides nothing.
 *
 * <p>It is the counterpart of {@link EngineLog}, and it exists for the same reason: this plugin keeps its own
 * port because hundreds of files name it, and only the engine's call sites cross the boundary. Production hands
 * the engine a {@code PaperScheduler} directly, so this is what a caller that already holds the port uses,
 * including every test fixture that drives the engine with its own threading.
 *
 * <p>The seven answers the engine never asks for throw rather than guessing. A silent no-op there would turn a
 * scheduling mistake into a window that never redraws, which is the failure hardest to find from a bug report.
 */
@NullMarked
public final class EngineScheduler {

    private EngineScheduler() {}

    /** {@code port} as the scheduler the menu engine takes. */
    public static com.uxplima.uxmlib.scheduler.Scheduler of(Scheduler port) {
        Objects.requireNonNull(port, "port");
        return new Adapter(port);
    }

    /** A cancelled flag over a task the port owns; the port's own handle is closed on cancel when it has one. */
    private static final class Handle implements TaskHandle {

        private final AtomicBoolean cancelled = new AtomicBoolean();

        private final @org.jspecify.annotations.Nullable AutoCloseable underlying;

        private Handle(@org.jspecify.annotations.Nullable AutoCloseable underlying) {
            this.underlying = underlying;
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true) && underlying != null) {
                try {
                    underlying.close();
                } catch (Exception failure) {
                    throw new IllegalStateException("cannot cancel the task", failure);
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }

    private static final class Adapter implements com.uxplima.uxmlib.scheduler.Scheduler {

        private final Scheduler port;

        private Adapter(Scheduler port) {
            this.port = port;
        }

        @Override
        public TaskHandle global(Runnable task) {
            port.onGlobal(task);
            return new Handle(null);
        }

        @Override
        public TaskHandle globalLater(Duration delay, Runnable task) {
            port.laterGlobal(delay, task);
            return new Handle(null);
        }

        @Override
        public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            Handle[] handle = new Handle[1];
            AutoCloseable underlying = port.repeatGlobal(() -> task.accept(handle[0]), delay, period);
            handle[0] = new Handle(underlying);
            return handle[0];
        }

        @Override
        public TaskHandle region(Location location, Runnable task) {
            port.onRegion(BukkitRefs.toPosition(location), task);
            return new Handle(null);
        }

        @Override
        public TaskHandle regionLater(Location location, Duration delay, Runnable task) {
            throw new UnsupportedOperationException("the menu engine asks for no delayed region task");
        }

        @Override
        public TaskHandle regionTimer(Location location, Duration delay, Duration period, Consumer<TaskHandle> task) {
            throw new UnsupportedOperationException("the menu engine asks for no region timer");
        }

        @Override
        public TaskHandle entity(Entity entity, Runnable task) {
            port.onEntity(BukkitRefs.toRef(player(entity)), task);
            return new Handle(null);
        }

        @Override
        public TaskHandle entityLater(Entity entity, Duration delay, Runnable task) {
            throw new UnsupportedOperationException("the menu engine asks for no delayed entity task");
        }

        @Override
        public TaskHandle entityTimer(Entity entity, Duration delay, Duration period, Consumer<TaskHandle> task) {
            throw new UnsupportedOperationException("the menu engine asks for no entity timer");
        }

        @Override
        public TaskHandle async(Runnable task) {
            port.async(task);
            return new Handle(null);
        }

        @Override
        public TaskHandle asyncLater(Duration delay, Runnable task) {
            port.asyncAfter(delay, task);
            return new Handle(null);
        }

        @Override
        public TaskHandle asyncTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            throw new UnsupportedOperationException("the menu engine asks for no async timer");
        }

        /** The port speaks in players, which is what the engine ever hands over: a viewer's own thread. */
        private static Player player(Entity entity) {
            if (entity instanceof Player player) {
                return player;
            }
            throw new IllegalArgumentException("this scheduler owns player threads only, was " + entity.getType());
        }
    }
}
