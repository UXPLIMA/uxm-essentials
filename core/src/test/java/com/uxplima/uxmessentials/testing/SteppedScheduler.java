package com.uxplima.uxmessentials.testing;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/**
 * Runs async work at once and delayed work when the test moves the clock past it. Entity work runs only for a
 * player marked online, and the retired branch runs for anyone else. Either lane can be told to refuse work, as a
 * disabling server's schedulers do.
 */
public final class SteppedScheduler implements Scheduler {

    private record Timed(Instant at, long order, Runnable task) {}

    private final MutableClock clock;
    private final List<Timed> waiting = new ArrayList<>();
    private final Set<UUID> online = new HashSet<>();
    private final Set<UUID> unloadedWorlds = new HashSet<>();
    private long scheduled;
    private boolean rejectsAsync;
    private boolean rejectsDelayed;

    public SteppedScheduler(MutableClock clock) {
        this.clock = clock;
    }

    public void online(PlayerRef player) {
        online.add(player.uuid());
    }

    public void offline(PlayerRef player) {
        online.remove(player.uuid());
    }

    /** A world this backend does not have loaded: region work there never runs, as on Folia. */
    public void unload(WorldRef world) {
        unloadedWorlds.add(world.uid());
    }

    public void rejectAsync(boolean rejects) {
        this.rejectsAsync = rejects;
    }

    public void rejectDelayed(boolean rejects) {
        this.rejectsDelayed = rejects;
    }

    /** Move the clock forward by {@code by}, running every delayed task that falls due on the way, in order. */
    public void advance(Duration by) {
        Instant until = clock.instant().plus(by);
        while (true) {
            Optional<Timed> next = waiting.stream()
                    .filter(timed -> !timed.at().isAfter(until))
                    .min(Comparator.comparing(Timed::at).thenComparingLong(Timed::order));
            if (next.isEmpty()) {
                break;
            }
            waiting.remove(next.get());
            clock.advance(Duration.between(clock.instant(), next.get().at()));
            next.get().task().run();
        }
        clock.advance(Duration.between(clock.instant(), until));
    }

    /** What a stopping server does to the delayed tasks it still holds. */
    public void dropWaiting() {
        waiting.clear();
    }

    @Override
    public void onGlobal(Runnable task) {
        task.run();
    }

    @Override
    public void onRegion(Position position, Runnable task) {
        if (!unloadedWorlds.contains(position.world().uid())) {
            task.run();
        }
    }

    @Override
    public void onRegion(Position position, Runnable task, Runnable retired) {
        if (unloadedWorlds.contains(position.world().uid())) {
            retired.run();
        } else {
            task.run();
        }
    }

    @Override
    public void onEntity(PlayerRef player, Runnable task) {
        if (online.contains(player.uuid())) {
            task.run();
        }
    }

    @Override
    public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
        if (online.contains(player.uuid())) {
            task.run();
        } else {
            retired.run();
        }
    }

    @Override
    public void async(Runnable task) {
        if (rejectsAsync) {
            throw new IllegalStateException("the async lane is closed");
        }
        task.run();
    }

    @Override
    public void asyncAfter(Duration delay, Runnable task) {
        if (rejectsDelayed) {
            throw new IllegalStateException("the delayed lane is closed");
        }
        waiting.add(new Timed(clock.instant().plus(delay), scheduled++, task));
    }
}
