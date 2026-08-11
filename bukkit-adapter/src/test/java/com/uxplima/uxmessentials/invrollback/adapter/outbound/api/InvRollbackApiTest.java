package com.uxplima.uxmessentials.invrollback.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.view.UxmSnapshot;
import com.uxplima.uxmessentials.api.view.UxmSnapshotCause;
import com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotRestorer;
import com.uxplima.uxmessentials.invrollback.adapter.outbound.InventorySnapshotCodec;
import com.uxplima.uxmessentials.invrollback.application.CaptureSnapshot;
import com.uxplima.uxmessentials.invrollback.application.RestoreSnapshot;
import com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import com.uxplima.uxmessentials.invrollback.domain.event.SnapshotRestored;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The published inventory-rollback surface: the list carries what a list needs and not the items, and the restore
 * runs the same flow the staff button does, safety copy, published fact and all.
 */
class InvRollbackApiTest {

    private static final Instant WHEN = Instant.parse("2026-07-17T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(WHEN, ZoneOffset.UTC);

    private ServerMock server;
    private RecordingRepository repository;
    private List<DomainEvent> published;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world"); // the safety snapshot records where the target was standing
        repository = new RecordingRepository();
        published = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theListCarriesTheFactsAndNotTheItems() {
        UUID owner = UUID.randomUUID();
        repository.save(Snapshot.capture(owner, SnapshotCause.DEATH, WHEN, new byte[0]));

        List<UxmSnapshot> listed = new InvRollbackQueries(repository, new QueryDoubles.InlineScheduler())
                .of(owner)
                .join();

        assertThat(listed).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.ownerId()).isEqualTo(owner);
            assertThat(snapshot.cause()).isEqualTo(UxmSnapshotCause.DEATH);
            assertThat(snapshot.takenAt()).isEqualTo(WHEN);
        });
    }

    @Test
    void restoringOverwritesTheInventoryTakesASafetyCopyAndAnnouncesTheFact() {
        PlayerMock target = server.addPlayer();
        ItemStack[] contents = new ItemStack[target.getInventory().getContents().length];
        contents[0] = new ItemStack(Material.DIAMOND, 5);
        Snapshot stored = Snapshot.capture(
                target.getUniqueId(),
                SnapshotCause.DEATH,
                WHEN.minusSeconds(60),
                InventorySnapshotCodec.encode(contents, null));
        repository.save(stored);
        target.getInventory().setItem(0, new ItemStack(Material.DIRT, 12));

        UxmOutcome outcome = actions(target)
                .restore(target.getUniqueId(), stored.id().value())
                .join();

        assertThat(outcome).isEqualTo(UxmOutcome.ok());
        assertThat(target.getInventory().getItem(0).getType()).isEqualTo(Material.DIAMOND);
        assertThat(repository.list(target.getUniqueId()))
                .anyMatch(snapshot -> snapshot.cause() == SnapshotCause.RESTORE);
        assertThat(published)
                .containsExactly(
                        new SnapshotRestored(ref(target), stored.id(), SnapshotCause.DEATH, WHEN.minusSeconds(60)));
    }

    @Test
    void aSnapshotThatNoLongerResolvesIsARefusalRatherThanASilentNoOp() {
        PlayerMock target = server.addPlayer();

        UxmOutcome outcome = actions(target)
                .restore(target.getUniqueId(), SnapshotId.random().value())
                .join();

        assertThat(outcome.failure().orElseThrow().code()).isEqualTo(UxmFailure.NOT_FOUND);
        assertThat(published).isEmpty();
    }

    @Test
    void aTargetWhoIsNotOnlineIsToldSoRatherThanHavingASnapshotWrittenToDisk() {
        UUID absent = UUID.randomUUID();

        UxmOutcome outcome = new InvRollbackActions(restorer(), new QueryDoubles.MapLookup())
                .restore(absent, SnapshotId.random().value())
                .join();

        assertThat(outcome.failure().orElseThrow().code()).isEqualTo(UxmFailure.PLAYER_OFFLINE);
        assertThat(repository.list(absent)).isEmpty();
    }

    private InvRollbackActions actions(PlayerMock target) {
        return new InvRollbackActions(restorer(), new QueryDoubles.MapLookup().with(ref(target)));
    }

    private SnapshotRestorer restorer() {
        RestoreSnapshot restore = new RestoreSnapshot(repository, new CaptureSnapshot(repository, 0));
        return new SnapshotRestorer(
                restore,
                new ActionDoubles.InlineScheduler(),
                (viewer, key, placeholders) -> key.key(),
                (viewer, renderedText) -> {},
                CLOCK,
                published::add);
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    /** An in-memory snapshot store, newest first, matching what the jOOQ one promises. */
    private static final class RecordingRepository implements SnapshotRepository {

        private final List<Snapshot> rows = new ArrayList<>();

        @Override
        public void save(Snapshot snapshot) {
            rows.add(snapshot);
        }

        @Override
        public List<Snapshot> list(UUID owner) {
            return rows.stream()
                    .filter(row -> row.owner().equals(owner))
                    .sorted(Comparator.comparing(Snapshot::createdAt).reversed())
                    .toList();
        }

        @Override
        public List<UUID> owners() {
            return rows.stream().map(Snapshot::owner).distinct().toList();
        }

        @Override
        public Optional<Snapshot> find(SnapshotId id) {
            return rows.stream().filter(row -> row.id().equals(id)).findFirst();
        }

        @Override
        public void delete(SnapshotId id) {
            rows.removeIf(row -> row.id().equals(id));
        }

        @Override
        public int deleteBeyondCount(UUID owner, int keep) {
            return 0;
        }

        @Override
        public int deleteOlderThan(Instant cutoff) {
            return 0;
        }
    }
}
