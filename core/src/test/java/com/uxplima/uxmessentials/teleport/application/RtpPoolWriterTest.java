package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.RtpPoolStore;
import com.uxplima.uxmessentials.teleport.domain.RtpColumn;
import org.junit.jupiter.api.Test;

/**
 * Pins the persist-on-validate path: the writer buffers validated columns and, on a refill flush, writes the
 * whole batch to the {@link RtpPoolStore} <em>off the tick thread</em> (through the {@link Scheduler}'s async
 * dispatch), never one synchronous row per candidate. It also proves the per-world de-dup by block {@code (x,z)},
 * the nothing-buffered no-op, and per-world isolation of a flush.
 */
class RtpPoolWriterTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final WorldRef NETHER = new WorldRef(UUID.randomUUID(), "world_nether");
    private static final Instant WHEN = Instant.parse("2026-07-02T00:00:00Z");

    private final RecordingStore store = new RecordingStore();
    private final RecordingScheduler scheduler = new RecordingScheduler();
    private final RtpPoolWriter writer = new RtpPoolWriter(store, scheduler, new NoopLogger());

    @Test
    void aFlushSavesTheBufferedBatchOffTheTickThread() {
        writer.record(new RtpColumn(WORLD, 100, 200, WHEN));
        writer.record(new RtpColumn(WORLD, 300, 400, WHEN));

        writer.flush(WORLD);

        assertThat(scheduler.asyncCount()).isEqualTo(1);
        assertThat(store.saved(WORLD)).hasSize(2);
        assertThat(store.saved(WORLD)).extracting(RtpColumn::x).containsExactlyInAnyOrder(100, 300);
    }

    @Test
    void repeatedValidationsOfOneColumnCollapseToTheFreshest() {
        writer.record(new RtpColumn(WORLD, 100, 200, WHEN));
        writer.record(new RtpColumn(WORLD, 100, 200, WHEN.plusSeconds(60)));

        writer.flush(WORLD);

        List<RtpColumn> saved = store.saved(WORLD);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).validatedAt()).isEqualTo(WHEN.plusSeconds(60));
    }

    @Test
    void flushingAnEmptyBufferWritesNothingAndSchedulesNoWork() {
        writer.flush(WORLD);

        assertThat(scheduler.asyncCount()).isZero();
        assertThat(store.saved(WORLD)).isEmpty();
    }

    @Test
    void aFlushDrainsOnlyItsOwnWorld() {
        writer.record(new RtpColumn(WORLD, 1, 1, WHEN));
        writer.record(new RtpColumn(NETHER, 2, 2, WHEN));

        writer.flush(WORLD);

        assertThat(store.saved(WORLD)).hasSize(1);
        assertThat(store.saved(NETHER)).isEmpty();

        writer.flush(NETHER);
        assertThat(store.saved(NETHER)).hasSize(1);
    }

    /** A {@link RtpPoolStore} that records the saved columns per world; the reads are unused by the writer. */
    private static final class RecordingStore implements RtpPoolStore {
        private final java.util.Map<UUID, List<RtpColumn>> byWorld = new java.util.HashMap<>();

        List<RtpColumn> saved(WorldRef world) {
            return byWorld.getOrDefault(world.uid(), List.of());
        }

        @Override
        public void save(WorldRef world, Collection<RtpColumn> columns) {
            byWorld.computeIfAbsent(world.uid(), id -> new ArrayList<>()).addAll(columns);
        }

        @Override
        public List<RtpColumn> load(WorldRef world, int limit) {
            return List.of();
        }

        @Override
        public int deleteStale(Duration olderThan) {
            return 0;
        }

        @Override
        public int count(WorldRef world) {
            return saved(world).size();
        }
    }

    /** A {@link Scheduler} that runs async work inline and counts the async dispatches (the off-tick hops). */
    private static final class RecordingScheduler implements Scheduler {
        private final AtomicInteger async = new AtomicInteger();

        int asyncCount() {
            return async.get();
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            async.incrementAndGet();
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
