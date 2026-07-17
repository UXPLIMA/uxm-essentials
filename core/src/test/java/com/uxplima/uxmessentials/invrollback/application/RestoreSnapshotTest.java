package com.uxplima.uxmessentials.invrollback.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage of {@link RestoreSnapshot} over an in-memory {@link SnapshotRepository}: a restore of a stored
 * snapshot first captures the target's current inventory as a {@link SnapshotCause#RESTORE} safety copy, then
 * returns the chosen snapshot for the adapter to apply; a restore of an unknown id returns empty and captures no
 * safety copy (so a stale click leaves no orphan snapshot).
 */
class RestoreSnapshotTest {

    private static final UUID TARGET = UUID.randomUUID();
    private static final Instant WHEN = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void safetySnapshotsTheCurrentStateThenReturnsTheChosenSnapshot() {
        FakeRepository repository = new FakeRepository();
        Snapshot stored = Snapshot.capture(TARGET, SnapshotCause.DEATH, WHEN.minusSeconds(60), new byte[] {7, 7});
        repository.save(stored);
        RestoreSnapshot restore = new RestoreSnapshot(repository, new CaptureSnapshot(repository, 0));

        byte[] currentInventory = {1, 2, 3};
        Optional<Snapshot> chosen = restore.restore(TARGET, stored.id(), currentInventory, WHEN);

        assertThat(chosen).isPresent();
        assertThat(chosen.get().id()).isEqualTo(stored.id());
        assertThat(chosen.get().contents()).containsExactly(7, 7);

        // The current inventory was frozen as a RESTORE safety snapshot before the caller overwrites it.
        List<Snapshot> safety = repository.list(TARGET).stream()
                .filter(s -> s.cause() == SnapshotCause.RESTORE)
                .toList();
        assertThat(safety).hasSize(1);
        assertThat(safety.get(0).createdAt()).isEqualTo(WHEN);
        assertThat(safety.get(0).contents()).containsExactly(currentInventory);
    }

    @Test
    void anUnknownIdReturnsEmptyAndCapturesNoSafetySnapshot() {
        FakeRepository repository = new FakeRepository();
        RestoreSnapshot restore = new RestoreSnapshot(repository, new CaptureSnapshot(repository, 0));

        Optional<Snapshot> chosen = restore.restore(TARGET, SnapshotId.random(), new byte[] {9}, WHEN);

        assertThat(chosen).isEmpty();
        assertThat(repository.list(TARGET)).isEmpty();
    }

    /** An in-memory {@link SnapshotRepository} sufficient for the restore orchestration under test. */
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
