package com.uxplima.uxmessentials.economy.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * A sweep through a Vault economy calls it on the server thread a few players per tick, so the tick never stalls, and
 * a wait that runs out stops the calls not yet made.
 */
class GlobalRegionLaneTest {

    private final Ticks ticks = new Ticks();

    @Test
    void theCallsRunOnTheServerThreadAFewPerTickInOrder() throws Exception {
        List<PlayerRef> players = players(7);
        GlobalRegionLane lane = new GlobalRegionLane(ticks, 3, Duration.ofSeconds(30));
        AtomicInteger calls = new AtomicInteger();

        CompletableFuture<List<String>> results = CompletableFuture.supplyAsync(
                () -> lane.each(players, player -> calls.incrementAndGet() + ":" + player.name(), "not run"));

        List<Integer> perTick = new ArrayList<>();
        while (!results.isDone()) {
            Runnable tick = ticks.next();
            if (tick == null) {
                continue;
            }
            int before = calls.get();
            tick.run();
            perTick.add(calls.get() - before);
        }
        assertThat(perTick).containsExactly(3, 3, 1);
        assertThat(results.get()).containsExactly("1:P0", "2:P1", "3:P2", "4:P3", "5:P4", "6:P5", "7:P6");
    }

    @Test
    void theCallsNotMadeWhenTheWaitRanOutAreNotMadeLater() throws Exception {
        List<PlayerRef> players = players(4);
        GlobalRegionLane lane = new GlobalRegionLane(ticks, 2, Duration.ofMillis(100));
        AtomicInteger calls = new AtomicInteger();

        List<String> results = lane.each(players, player -> "paid " + calls.incrementAndGet(), "not run");

        assertThat(results).containsOnly("not run");
        // The server thread gets to the queued tick only now: it must not pay anyone.
        Runnable late = ticks.next();
        if (late != null) {
            late.run();
        }
        assertThat(calls).hasValue(0);
    }

    private static List<PlayerRef> players(int count) {
        List<PlayerRef> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            players.add(new PlayerRef(new UUID(0L, i + 1L), "P" + i));
        }
        return players;
    }

    /** The global region's queue, run one tick at a time by the test. */
    private static final class Ticks implements Scheduler {

        private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

        @Nullable Runnable next() throws InterruptedException {
            return queue.poll(50, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onGlobal(Runnable task) {
            queue.add(task);
        }

        @Override
        public void onRegion(Position position, Runnable task) {}

        @Override
        public void onEntity(PlayerRef player, Runnable task) {}

        @Override
        public void async(Runnable task) {}

        @Override
        public void asyncAfter(Duration delay, Runnable task) {}
    }
}
