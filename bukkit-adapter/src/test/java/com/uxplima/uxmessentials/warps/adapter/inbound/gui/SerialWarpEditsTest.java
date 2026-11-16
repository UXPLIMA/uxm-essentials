package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;

/**
 * The editor's saves leave the click's thread, and two edits of one warp still happen one after the other, so the
 * second reads what the first wrote.
 */
class SerialWarpEditsTest {

    private final AsyncLane lane = new AsyncLane();
    private final SerialWarpEdits edits = new SerialWarpEdits(lane);
    private final List<String> done = new ArrayList<>();

    @Test
    void anEditRunsOnTheAsyncLaneNotOnTheCallersThread() {
        edits.run("server:spawn", () -> done.add("lock"));

        assertThat(done).isEmpty();
        lane.runAll();
        assertThat(done).containsExactly("lock");
    }

    @Test
    void aSecondEditOfTheSameWarpWaitsUntilTheFirstHasFinished() {
        edits.run("server:spawn", () -> done.add("lock"));
        edits.run("server:spawn", () -> done.add("unlock"));

        assertThat(lane.waiting()).isOne();
        lane.runNext();
        assertThat(done).containsExactly("lock");
        lane.runAll();
        assertThat(done).containsExactly("lock", "unlock");
    }

    @Test
    void editsOfDifferentWarpsDoNotWaitForEachOther() {
        edits.run("server:spawn", () -> done.add("spawn"));
        edits.run("server:shop", () -> done.add("shop"));

        assertThat(lane.waiting()).isEqualTo(2);
    }

    @Test
    void aFailedEditStillLetsTheNextEditOfThatWarpRun() {
        edits.run("server:spawn", () -> {
            throw new IllegalStateException("database down");
        });
        edits.run("server:spawn", () -> done.add("unlock"));

        assertThatThrownBy(lane::runNext).hasMessage("database down");
        lane.runAll();
        assertThat(done).containsExactly("unlock");
    }

    @Test
    void anEditTheLaneRefusesDoesNotHoldBackTheNextOne() {
        lane.refuse = true;
        edits.run("server:spawn", () -> done.add("lock"));
        lane.refuse = false;

        edits.run("server:spawn", () -> done.add("unlock"));
        lane.runAll();

        assertThat(done).containsExactly("unlock");
    }

    /** An async lane that holds its tasks until the test runs them, and can refuse work as a stopping plugin does. */
    private static final class AsyncLane implements Scheduler {

        private final Deque<Runnable> tasks = new ArrayDeque<>();
        boolean refuse;

        int waiting() {
            return tasks.size();
        }

        void runNext() {
            tasks.removeFirst().run();
        }

        void runAll() {
            while (!tasks.isEmpty()) {
                runNext();
            }
        }

        @Override
        public void async(Runnable task) {
            if (refuse) {
                throw new IllegalStateException("the async lane is closed");
            }
            tasks.addLast(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onGlobal(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            throw new UnsupportedOperationException();
        }
    }
}
