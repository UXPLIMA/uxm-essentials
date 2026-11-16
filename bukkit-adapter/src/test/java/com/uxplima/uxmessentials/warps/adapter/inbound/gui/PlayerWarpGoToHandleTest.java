package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

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
 * The editor's "go to" button is clicked on the viewer's own thread. The player-warp use behind it reads the warp
 * and records the visit in storage, so it has to run on the async lane, as the {@code /pwarp} command does.
 */
class PlayerWarpGoToHandleTest {

    private static final PlayerRef VIEWER =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Alex");
    private static final PlayerRef OWNER =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a2"), "Sam");

    @Test
    void theGoToRunsOnTheAsyncLaneNotOnTheClickingThread() {
        Threads threads = new Threads();
        List<String> uses = new ArrayList<>();
        PlayerWarpGoToHandle handle = new PlayerWarpGoToHandle();
        handle.bind(
                threads,
                (viewer, owner, name) ->
                        uses.add(threads.current + ":" + viewer.name() + "->" + owner.name() + "/" + name));

        threads.onEntity(VIEWER, () -> handle.teleport(VIEWER, OWNER, "spawn"));
        threads.runAll();

        assertThat(uses).containsExactly("async:Alex->Sam/spawn");
    }

    /** Holds every task until the test runs it, naming the lane each one runs on. */
    private static final class Threads implements Scheduler {

        private record Task(String lane, Runnable work) {}

        private final Deque<Task> queued = new ArrayDeque<>();
        private String current = "test";

        void runAll() {
            Task next;
            while ((next = queued.pollFirst()) != null) {
                String before = current;
                current = next.lane();
                try {
                    next.work().run();
                } finally {
                    current = before;
                }
            }
        }

        @Override
        public void onGlobal(Runnable task) {
            queued.addLast(new Task("global", task));
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            queued.addLast(new Task("region", task));
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            queued.addLast(new Task("entity", task));
        }

        @Override
        public void async(Runnable task) {
            queued.addLast(new Task("async", task));
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            queued.addLast(new Task("async", task));
        }
    }
}
