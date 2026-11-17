package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;

import com.uxplima.uxmessentials.economy.application.port.ProviderLane;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link ProviderLane} for a Vault economy, which expects its calls on the server thread: the calls run on the
 * global region a few per tick, so a sweep over many thousands of players never stalls a tick, while the caller off
 * the tick waits for the group. A wait that runs out (the server is stopping) stops the calls not yet made, and those
 * answer "not run".
 */
@NullMarked
public final class GlobalRegionLane implements ProviderLane {

    /** Calls per tick: a Vault call is a map lookup or one write of its own, so this stays far below a tick. */
    static final int PER_TICK = 10;

    private final Scheduler scheduler;
    private final int perTick;
    private final Duration wait;

    public GlobalRegionLane(Scheduler scheduler) {
        this(scheduler, PER_TICK, Duration.ofMinutes(5));
    }

    GlobalRegionLane(Scheduler scheduler, int perTick, Duration wait) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.wait = Objects.requireNonNull(wait, "wait");
        if (perTick <= 0) {
            throw new IllegalArgumentException("perTick must be positive: " + perTick);
        }
        this.perTick = perTick;
    }

    @Override
    public <T> List<T> each(List<PlayerRef> owners, Function<PlayerRef, T> work, T notRun) {
        Objects.requireNonNull(owners, "owners");
        Objects.requireNonNull(work, "work");
        int size = owners.size();
        if (size == 0) {
            return List.of();
        }
        AtomicReferenceArray<T> results = new AtomicReferenceArray<>(size);
        for (int i = 0; i < size; i++) {
            results.set(i, notRun);
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        AtomicBoolean abandoned = new AtomicBoolean();
        scheduler.onGlobal(new Batch<>(owners, work, results, done, abandoned, 0));
        try {
            done.get(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            abandoned.set(true);
            Thread.currentThread().interrupt();
        } catch (TimeoutException | ExecutionException stopped) {
            abandoned.set(true);
        }
        List<T> answered = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            answered.add(results.get(i));
        }
        return answered;
    }

    /** One tick's calls, which schedules the next tick's until every owner is done or the caller gave up. */
    private final class Batch<T> implements Runnable {

        private final List<PlayerRef> owners;
        private final Function<PlayerRef, T> work;
        private final AtomicReferenceArray<T> results;
        private final CompletableFuture<Void> done;
        private final AtomicBoolean abandoned;
        private final int from;

        Batch(
                List<PlayerRef> owners,
                Function<PlayerRef, T> work,
                AtomicReferenceArray<T> results,
                CompletableFuture<Void> done,
                AtomicBoolean abandoned,
                int from) {
            this.owners = owners;
            this.work = work;
            this.results = results;
            this.done = done;
            this.abandoned = abandoned;
            this.from = from;
        }

        @Override
        public void run() {
            int end = Math.min(owners.size(), from + perTick);
            for (int i = from; i < end; i++) {
                if (abandoned.get()) {
                    done.complete(null);
                    return;
                }
                results.set(i, work.apply(owners.get(i)));
            }
            if (end >= owners.size()) {
                done.complete(null);
                return;
            }
            scheduler.onGlobal(new Batch<>(owners, work, results, done, abandoned, end));
        }
    }
}
