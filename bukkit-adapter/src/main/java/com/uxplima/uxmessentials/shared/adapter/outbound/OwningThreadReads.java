package com.uxplima.uxmessentials.shared.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * Answers a question about the server on the thread that owns what it reads, for an adapter whose port is asked from
 * any thread.
 *
 * <p>Use cases that read the database run on the async lane and still ask what a player may do or what a block is,
 * through ports whose answer is read from the Bukkit API. Such a read is only allowed on the thread that owns the
 * player (their entity thread) or the position (its region thread), so:
 *
 * <ul>
 *   <li>on the owning thread, it reads in place;
 *   <li>off every tick thread, and on the global thread, it sends the read to the owner and waits for the answer.
 *       Nothing that runs on a region thread waits for the global thread, so that wait cannot close a cycle;
 *   <li>on another region's thread it does not wait, since that region may be waiting on this one, and answers the
 *       caller's {@code unanswered} value.
 * </ul>
 *
 * <p>The caller names two answers. {@code gone} is the true answer when there is nothing to read: the player left,
 * the world is not loaded. {@code unanswered} is the safe answer when the read could not be made: the owner did not
 * run it within the bound, the owner is another region, or the plugin is stopping. They differ where failing open
 * would hand a player something: a combat tag that cannot be read in time counts as a tag.
 */
@NullMarked
public final class OwningThreadReads {

    /** Several ticks of a lagging server, far beyond a healthy hop, which lands on the next tick. */
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    /** At most one stall line per this long, carrying how many stalls it stands for. */
    static final Duration STALL_LOG_INTERVAL = Duration.ofMinutes(1);

    /** What the calling thread is and owns. */
    public interface Threads {

        /** True on any tick thread: the main thread on Paper, the global or a region thread on Folia. */
        boolean onTickThread();

        /** True on the global region thread. */
        boolean onGlobalThread();

        /** True when this thread owns {@code player}'s entity. */
        boolean ownsPlayer(PlayerRef player);

        /** True when this thread owns the region holding {@code at}. */
        boolean ownsPosition(Position at);

        /** True once the plugin is disabling, when no hop it schedules will run. */
        boolean stopping();
    }

    private final Scheduler scheduler;
    private final Threads threads;
    private final Duration timeout;
    private final Logger log;
    private final LongSupplier nanoTime;
    private final AtomicInteger unreportedStalls = new AtomicInteger();
    private final AtomicLong lastStallReport = new AtomicLong(Long.MIN_VALUE);

    public OwningThreadReads(
            Scheduler scheduler, Threads threads, Duration timeout, Logger log, LongSupplier nanoTime) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.threads = Objects.requireNonNull(threads, "threads");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.log = Objects.requireNonNull(log, "log");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    /** Reads on {@code plugin}'s server, waiting up to the default bound for a hop. */
    public static OwningThreadReads onServer(Scheduler scheduler, Plugin plugin, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        return new OwningThreadReads(
                scheduler, new ServerThreads(scheduler, plugin), DEFAULT_TIMEOUT, log, System::nanoTime);
    }

    /**
     * {@code read} on {@code player}'s entity thread; {@code gone} when they are not online here, {@code unanswered}
     * when the read could not be made.
     */
    public <T> T forPlayer(PlayerRef player, Supplier<T> read, T gone, T unanswered) {
        Objects.requireNonNull(player, "player");
        if (player.isSystem()) {
            // The console has no entity to read, and no entity thread to wait on.
            return Objects.requireNonNull(gone, "gone");
        }
        return answer(
                read,
                gone,
                unanswered,
                () -> threads.ownsPlayer(player),
                (task, retired) -> scheduler.onEntity(player, task, retired));
    }

    /**
     * {@code read} on the region thread owning {@code at}; {@code gone} when its world is not loaded,
     * {@code unanswered} when the read could not be made.
     */
    public <T> T at(Position at, Supplier<T> read, T gone, T unanswered) {
        Objects.requireNonNull(at, "at");
        return answer(
                read,
                gone,
                unanswered,
                () -> threads.ownsPosition(at),
                (task, retired) -> scheduler.onRegion(at, task, retired));
    }

    private <T> T answer(Supplier<T> read, T gone, T unanswered, Owner owner, Hop hop) {
        Objects.requireNonNull(read, "read");
        Objects.requireNonNull(gone, "gone");
        Objects.requireNonNull(unanswered, "unanswered");
        if (threads.stopping()) {
            return unanswered;
        }
        if (threads.onTickThread()) {
            if (owner.ownsIt()) {
                return read.get();
            }
            if (!threads.onGlobalThread()) {
                return unanswered;
            }
        }
        CompletableFuture<T> answer = new CompletableFuture<>();
        try {
            hop.schedule(() -> settle(answer, read), () -> answer.complete(gone));
        } catch (RuntimeException refused) {
            return unanswered;
        }
        return await(answer, unanswered);
    }

    private static <T> void settle(CompletableFuture<T> answer, Supplier<T> read) {
        try {
            answer.complete(read.get());
        } catch (RuntimeException failure) {
            answer.completeExceptionally(failure);
        }
    }

    private <T> T await(CompletableFuture<T> answer, T unanswered) {
        try {
            return answer.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException stalled) {
            // Settled now so a read that lands later has nobody left to answer.
            answer.complete(unanswered);
            reportStall();
            return unanswered;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return unanswered;
        } catch (ExecutionException failed) {
            // The read threw on the owning thread; the caller hears it as if it had read in place.
            if (failed.getCause() instanceof RuntimeException failure) {
                throw failure;
            }
            throw new IllegalStateException("owning thread read failed", failed.getCause());
        }
    }

    /** One line per interval at most, so a server that lags for an hour says so without a line per question. */
    private void reportStall() {
        unreportedStalls.incrementAndGet();
        long now = nanoTime.getAsLong();
        long last = lastStallReport.get();
        if (last != Long.MIN_VALUE && now - last < STALL_LOG_INTERVAL.toNanos()) {
            return;
        }
        if (lastStallReport.compareAndSet(last, now)) {
            int stalls = unreportedStalls.getAndSet(0);
            log.warn(
                    "event=owning_thread_read_timeout timeout_ms={} stalls={}",
                    String.valueOf(timeout.toMillis()),
                    String.valueOf(stalls));
        }
    }

    /** Whether the calling thread owns what is read. */
    @FunctionalInterface
    private interface Owner {
        boolean ownsIt();
    }

    /** One way to reach the owning thread: run {@code task} there, or {@code retired} when it never can. */
    @FunctionalInterface
    private interface Hop {
        void schedule(Runnable task, Runnable retired);
    }

    /** The live server's answers. */
    private record ServerThreads(Scheduler scheduler, Plugin plugin) implements Threads {

        @Override
        public boolean onTickThread() {
            return Bukkit.isPrimaryThread();
        }

        @Override
        public boolean onGlobalThread() {
            return scheduler.onGlobalThread();
        }

        @Override
        public boolean ownsPlayer(PlayerRef player) {
            return scheduler.ownsEntity(player);
        }

        @Override
        public boolean ownsPosition(Position at) {
            World world = Bukkit.getWorld(at.world().uid());
            return world != null && Bukkit.isOwnedByCurrentRegion(world, at.blockX() >> 4, at.blockZ() >> 4);
        }

        @Override
        public boolean stopping() {
            return !plugin.isEnabled();
        }
    }
}
