package com.uxplima.uxmessentials.invrollback.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.invrollback.application.PruneSnapshots;
import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.invrollback.domain.RetentionPolicy;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;

/**
 * Coverage of {@link SnapshotPruneSweep}: a tick drives the {@link PruneSnapshots} sweep (removing over-count and
 * over-age rows) on the async lane, and a disabled retention policy schedules no repeating task.
 */
class SnapshotPruneSweepTest {

    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID OWNER = UUID.randomUUID();

    @Test
    void aTickPrunesOverCountAndOverAgeSnapshots() {
        FakeRepository repository = new FakeRepository();
        repository.save(snapshot(NOW.minusSeconds(30)));
        repository.save(snapshot(NOW.minusSeconds(20)));
        repository.save(snapshot(NOW.minusSeconds(10)));
        repository.save(snapshot(NOW.minus(Duration.ofDays(45))));
        PruneSnapshots prune = new PruneSnapshots(repository, new RetentionPolicy(2, 30));

        new SnapshotPruneSweep(prune, new InlineScheduler(), CLOCK, true).tick();

        // Count cap keeps the newest 2; the ancient row is also gone by age.
        assertThat(repository.list(OWNER))
                .extracting(Snapshot::createdAt)
                .containsExactly(NOW.minusSeconds(10), NOW.minusSeconds(20));
    }

    @Test
    void aDisabledPolicySchedulesNoRepeatingTask() {
        FakeRepository repository = new FakeRepository();
        CountingScheduler scheduler = new CountingScheduler();
        PruneSnapshots prune = new PruneSnapshots(repository, new RetentionPolicy(0, 0));

        new SnapshotPruneSweep(prune, scheduler, CLOCK, false).start();

        assertThat(scheduler.repeatCount()).isZero();
    }

    private static Snapshot snapshot(Instant createdAt) {
        return Snapshot.of(SnapshotId.random(), OWNER, SnapshotCause.DEATH, createdAt, new byte[] {1});
    }

    /** A scheduler whose async hop runs inline; repeatGlobal is never expected on the tick path. */
    private static class InlineScheduler implements Scheduler {
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
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** Counts repeatGlobal registrations so the disabled path can assert none was scheduled. */
    private static final class CountingScheduler extends InlineScheduler {
        private final AtomicInteger repeats = new AtomicInteger();

        int repeatCount() {
            return repeats.get();
        }

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            repeats.incrementAndGet();
            return () -> {};
        }
    }

    /** An in-memory {@link SnapshotRepository} implementing the two prune primitives the way the jOOQ adapter does. */
    private static final class FakeRepository implements SnapshotRepository {
        private final List<Snapshot> rows = new ArrayList<>();

        @Override
        public void save(Snapshot snapshot) {
            rows.add(snapshot);
        }

        @Override
        public List<Snapshot> list(UUID owner) {
            return rows.stream()
                    .filter(s -> s.owner().equals(owner))
                    .sorted(Comparator.comparing(Snapshot::createdAt).reversed())
                    .toList();
        }

        @Override
        public List<UUID> owners() {
            return rows.stream().map(Snapshot::owner).distinct().toList();
        }

        @Override
        public Optional<Snapshot> find(SnapshotId id) {
            return rows.stream().filter(s -> s.id().equals(id)).findFirst();
        }

        @Override
        public void delete(SnapshotId id) {
            rows.removeIf(s -> s.id().equals(id));
        }

        @Override
        public int deleteBeyondCount(UUID owner, int keep) {
            List<Snapshot> newestFirst = list(owner);
            List<Snapshot> stale =
                    newestFirst.size() > keep ? newestFirst.subList(keep, newestFirst.size()) : List.of();
            rows.removeAll(stale);
            return stale.size();
        }

        @Override
        public int deleteOlderThan(Instant cutoff) {
            int before = rows.size();
            rows.removeIf(s -> s.createdAt().isBefore(cutoff));
            return before - rows.size();
        }
    }
}
