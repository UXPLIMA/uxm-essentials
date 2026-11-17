package com.uxplima.uxmessentials.security.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * One player's session writes land in the order they were handed in, however the async lane schedules them: a release
 * handed in after a slow record waits for it, and another player's work does not wait.
 */
class PlayerLaneTest {

    private static final UUID ADA = new UUID(0L, 7L);
    private static final UUID BOB = new UUID(0L, 8L);

    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private final PlayerLane lane = new PlayerLane(new Pooled(pool), new Errors(errors));

    @AfterEach
    void stop() {
        pool.shutdownNow();
    }

    @Test
    void aReleaseHandedInAfterASlowRecordLandsAfterIt() throws Exception {
        List<String> landed = new CopyOnWriteArrayList<>();
        CountDownLatch recordMayFinish = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(3);

        lane.run(ADA, "record_failed", () -> {
            await(recordMayFinish);
            landed.add("record");
            done.countDown();
        });
        lane.run(ADA, "release_failed", () -> {
            landed.add("release");
            done.countDown();
        });
        lane.run(BOB, "record_failed", () -> {
            landed.add("bob");
            done.countDown();
            recordMayFinish.countDown();
        });

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(landed).containsExactly("bob", "record", "release");
    }

    @Test
    void aFailureIsLoggedAndTheWorkAfterItStillRuns() throws Exception {
        CountDownLatch after = new CountDownLatch(1);

        lane.run(ADA, "record_failed", () -> {
            throw new IllegalStateException("database down");
        });
        lane.run(ADA, "release_failed", after::countDown);

        assertThat(after.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(errors).containsExactly("event=record_failed player=" + ADA);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    /** An async lane of several threads, so tasks handed in one after another may run in any order. */
    private record Pooled(ExecutorService pool) implements Scheduler {

        @Override
        public void async(Runnable task) {
            pool.execute(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            throw new AssertionError("only the async lane is used");
        }

        @Override
        public void onGlobal(Runnable task) {
            throw new AssertionError("only the async lane is used");
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new AssertionError("only the async lane is used");
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            throw new AssertionError("only the async lane is used");
        }
    }

    private record Errors(List<String> lines) implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {
            lines.add(message);
        }

        @Override
        public void debug(String message, Object... args) {}
    }
}
