package com.uxplima.uxmessentials.invrollback.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.invrollback.application.port.PendingRestores;
import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import org.junit.jupiter.api.Test;

/**
 * A restore chosen for a player who is not online is kept with a copy of its snapshot and handed out once, at the
 * player's next join, after a safety copy of what they hold then.
 */
class RestoreSnapshotTest {

    private static final UUID IVY = UUID.fromString("00000000-0000-0000-0000-0000000001e1");
    private static final Instant DIED = Instant.parse("2030-01-01T10:00:00Z");
    private static final Instant JOINED = Instant.parse("2030-01-01T12:00:00Z");

    private final Snapshots snapshots = new Snapshots();
    private final KeptRestores kept = new KeptRestores();
    private final RestoreSnapshot restore = new RestoreSnapshot(snapshots, new CaptureSnapshot(snapshots, 10), kept);

    @Test
    void aRestoreKeptForAPlayerWhoIsAwayIsHandedOutAtTheirNextJoin() {
        Snapshot death = Snapshot.capture(IVY, SnapshotCause.DEATH, DIED, new byte[] {1, 2, 3});
        snapshots.save(death);

        assertThat(restore.keepForNextJoin(IVY, death.id())).contains(death);

        assertThat(restore.takeKept(IVY)).containsExactly(death);
        assertThat(restore.takeKept(IVY)).isEmpty();
    }

    @Test
    void aKeptRestoreOutlivesItsSnapshot() {
        Snapshot death = Snapshot.capture(IVY, SnapshotCause.DEATH, DIED, new byte[] {1, 2, 3});
        snapshots.save(death);
        restore.keepForNextJoin(IVY, death.id());

        snapshots.delete(death.id());

        assertThat(restore.takeKept(IVY)).containsExactly(death);
    }

    @Test
    void aSnapshotThatIsGoneKeepsNothing() {
        assertThat(restore.keepForNextJoin(IVY, SnapshotId.random())).isEmpty();
        assertThat(restore.takeKept(IVY)).isEmpty();
    }

    @Test
    void aJoinThatCouldNotApplyTheRestoreKeepsItForTheNextOne() {
        Snapshot death = Snapshot.capture(IVY, SnapshotCause.DEATH, DIED, new byte[] {1, 2, 3});
        snapshots.save(death);
        restore.keepForNextJoin(IVY, death.id());

        restore.keepAgain(IVY, restore.takeKept(IVY));

        assertThat(restore.takeKept(IVY)).containsExactly(death);
    }

    @Test
    void theSafetyCopyKeepsWhatThePlayerHeldAsTheyJoined() {
        restore.safetyCopy(IVY, new byte[] {9}, JOINED);

        assertThat(snapshots.list(IVY)).singleElement().satisfies(copy -> {
            assertThat(copy.cause()).isEqualTo(SnapshotCause.RESTORE);
            assertThat(copy.createdAt()).isEqualTo(JOINED);
            assertThat(copy.contents()).containsExactly(9);
        });
    }

    /** The snapshot store, newest first per owner. */
    private static final class Snapshots implements SnapshotRepository {

        private final Map<SnapshotId, Snapshot> rows = new LinkedHashMap<>();

        @Override
        public void save(Snapshot snapshot) {
            rows.put(snapshot.id(), snapshot);
        }

        @Override
        public List<Snapshot> list(UUID owner) {
            return rows.values().stream()
                    .filter(snapshot -> snapshot.owner().equals(owner))
                    .sorted(Comparator.comparing(Snapshot::createdAt).reversed())
                    .toList();
        }

        @Override
        public List<UUID> owners() {
            return rows.values().stream().map(Snapshot::owner).distinct().toList();
        }

        @Override
        public Optional<Snapshot> find(SnapshotId id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public void delete(SnapshotId id) {
            rows.remove(id);
        }

        @Override
        public int deleteBeyondCount(UUID owner, int keep) {
            List<Snapshot> owned = list(owner);
            List<Snapshot> beyond = owned.subList(Math.min(keep, owned.size()), owned.size());
            beyond.forEach(snapshot -> rows.remove(snapshot.id()));
            return beyond.size();
        }

        @Override
        public int deleteOlderThan(Instant cutoff) {
            List<SnapshotId> old = rows.values().stream()
                    .filter(snapshot -> snapshot.createdAt().isBefore(cutoff))
                    .map(Snapshot::id)
                    .toList();
            old.forEach(rows::remove);
            return old.size();
        }
    }

    /** The kept restores, as the database holds them. */
    private static final class KeptRestores implements PendingRestores {

        private final Map<UUID, List<Snapshot>> rows = new HashMap<>();

        @Override
        public void keep(UUID player, Snapshot snapshot) {
            rows.computeIfAbsent(player, id -> new ArrayList<>()).add(snapshot);
        }

        @Override
        public List<Snapshot> take(UUID player) {
            List<Snapshot> taken = rows.remove(player);
            return taken == null ? List.of() : List.copyOf(taken);
        }
    }
}
