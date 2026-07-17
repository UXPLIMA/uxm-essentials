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
 * Unit coverage of {@link CaptureSnapshot} over an in-memory {@link SnapshotRepository}: a capture persists the
 * built {@link Snapshot} with the right owner, cause, instant and contents, and enforces the per-player count cap
 * after the save (and skips the trim when the cap is disabled).
 */
class CaptureSnapshotTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final Instant WHEN = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void savesTheSnapshotWithItsOwnerCauseInstantAndContents() {
        FakeRepository repository = new FakeRepository();
        CaptureSnapshot capture = new CaptureSnapshot(repository, 0);

        byte[] contents = {4, 8, 15, 16, 23, 42};
        Snapshot saved = capture.capture(OWNER, SnapshotCause.DEATH, contents, WHEN);

        assertThat(repository.rows).containsExactly(saved);
        assertThat(saved.owner()).isEqualTo(OWNER);
        assertThat(saved.cause()).isEqualTo(SnapshotCause.DEATH);
        assertThat(saved.createdAt()).isEqualTo(WHEN);
        assertThat(saved.contents()).containsExactly(contents);
    }

    @Test
    void trimsToTheCountCapAfterEachSave() {
        FakeRepository repository = new FakeRepository();
        CaptureSnapshot capture = new CaptureSnapshot(repository, 2);

        capture.capture(OWNER, SnapshotCause.LOGOUT, new byte[] {1}, WHEN.minusSeconds(30));
        capture.capture(OWNER, SnapshotCause.LOGOUT, new byte[] {2}, WHEN.minusSeconds(20));
        capture.capture(OWNER, SnapshotCause.LOGOUT, new byte[] {3}, WHEN.minusSeconds(10));

        // Three saved, but the cap of 2 pruned the oldest after the third save.
        assertThat(repository.list(OWNER)).hasSize(2);
        assertThat(repository.list(OWNER)).allSatisfy(s -> assertThat(s.owner()).isEqualTo(OWNER));
    }

    @Test
    void aDisabledCapNeverTrims() {
        FakeRepository repository = new FakeRepository();
        CaptureSnapshot capture = new CaptureSnapshot(repository, 0);

        for (int i = 0; i < 5; i++) {
            capture.capture(OWNER, SnapshotCause.DEATH, new byte[] {(byte) i}, WHEN.plusSeconds(i));
        }

        assertThat(repository.list(OWNER)).hasSize(5);
    }

    /** An in-memory {@link SnapshotRepository} that implements the count cap the same way the jOOQ adapter does. */
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
            int removed = stale.size();
            rows.removeAll(stale);
            return removed;
        }

        @Override
        public int deleteOlderThan(Instant cutoff) {
            int before = rows.size();
            rows.removeIf(s -> s.createdAt().isBefore(cutoff));
            return before - rows.size();
        }
    }
}
