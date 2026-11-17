package com.uxplima.uxmessentials.economy.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;

/**
 * The passes handing the players online their deliveries never overlap, a burst of requests is folded into one more
 * pass, and a pass that fails anywhere still lets the next request run.
 */
class OnlineItemHandoverTest {

    private static final PlayerRef ALEX = new PlayerRef(new UUID(0L, 1L), "Alex");

    private final Lanes lanes = new Lanes();
    private final RecordingLog log = new RecordingLog();
    private final List<List<PlayerRef>> passes = new ArrayList<>();
    private int rosterFailures;
    private int handOverFailures;
    private final OnlineItemHandover handover = new OnlineItemHandover(
            lanes,
            () -> {
                if (rosterFailures > 0) {
                    rosterFailures--;
                    throw new IllegalStateException("roster unreadable");
                }
                return List.of(ALEX);
            },
            online -> {
                if (handOverFailures > 0) {
                    handOverFailures--;
                    throw new IllegalStateException("database unreachable");
                }
                passes.add(online);
            },
            log);

    @Test
    void aBurstOfRequestsIsFoldedIntoOneMorePass() {
        handover.run();
        handover.run();
        handover.run();

        lanes.drain();

        assertThat(passes).hasSize(2).allSatisfy(online -> assertThat(online).containsExactly(ALEX));
    }

    @Test
    void aRequestWhileAPassRunsGetsOneMorePassAfterIt() {
        handover.run();
        lanes.runNext();
        // The roster is read; the hand-over waits on the async lane.
        handover.run();
        assertThat(lanes.pending()).isEqualTo(1);

        lanes.drain();

        assertThat(passes).hasSize(2);
    }

    @Test
    void aPassWhoseRosterFailedLetsTheNextRequestRun() {
        rosterFailures = 1;
        handover.run();
        lanes.drain();
        assertThat(passes).isEmpty();
        assertThat(log.errors).isNotEmpty();

        handover.run();
        lanes.drain();

        assertThat(passes).hasSize(1);
    }

    @Test
    void aPassWhoseHandOverFailedLetsTheNextRequestRun() {
        handOverFailures = 1;
        handover.run();
        lanes.drain();

        handover.run();
        lanes.drain();

        assertThat(passes).hasSize(1);
    }

    @Test
    void aTaskTheSchedulerRefusedLetsTheNextRequestRun() {
        lanes.refuseGlobal = 1;
        handover.run();
        lanes.refuseAsync = 1;
        handover.run();
        lanes.drain();
        assertThat(passes).isEmpty();

        handover.run();
        lanes.drain();

        assertThat(passes).hasSize(1);
        assertThat(log.errors).hasSize(2);
    }

    /** The global region and the async lane as one queue the test runs, refusing a task when told to. */
    private static final class Lanes implements Scheduler {

        private final Deque<Runnable> tasks = new ArrayDeque<>();
        int refuseGlobal;
        int refuseAsync;

        void runNext() {
            Runnable next = tasks.pollFirst();
            if (next != null) {
                next.run();
            }
        }

        void drain() {
            while (!tasks.isEmpty()) {
                runNext();
            }
        }

        int pending() {
            return tasks.size();
        }

        @Override
        public void onGlobal(Runnable task) {
            if (refuseGlobal > 0) {
                refuseGlobal--;
                throw new IllegalStateException("plugin disabled");
            }
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
            if (refuseAsync > 0) {
                refuseAsync--;
                throw new IllegalStateException("executor shut down");
            }
            tasks.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            tasks.add(task);
        }
    }
}
