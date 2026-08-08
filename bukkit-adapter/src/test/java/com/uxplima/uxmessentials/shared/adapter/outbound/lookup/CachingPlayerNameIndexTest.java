package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameRepository;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Behaviour of the in-memory name index: a name recorded on join resolves in any case, the durable store is only
 * consulted through the warm and the write-behind, and the newest account wins when two share a lower-cased name
 * (the offline-mode case-variant case, and the online-mode name-change case).
 */
class CachingPlayerNameIndexTest {

    private RecordingRepository repository;
    private CachingPlayerNameIndex index;

    @BeforeEach
    void setUp() {
        repository = new RecordingRepository();
        index = new CachingPlayerNameIndex(new ImmediateScheduler(), new NoopLogger());
    }

    @Test
    void resolvesARecordedNameIgnoringCase() {
        UUID player = UUID.randomUUID();
        index.record(player, "Cofteey");

        assertThat(index.byName("cofteey")).contains(new PlayerRef(player, "Cofteey"));
        assertThat(index.byName("COFTEEY")).contains(new PlayerRef(player, "Cofteey"));
    }

    @Test
    void keepsTheOriginalCaseOfTheNameForRendering() {
        UUID player = UUID.randomUUID();
        index.record(player, "Cofteey");

        assertThat(index.byName("cofteey").orElseThrow().name()).isEqualTo("Cofteey");
    }

    @Test
    void unknownNameResolvesToEmpty() {
        assertThat(index.byName("nobody")).isEmpty();
    }

    @Test
    void blankNameResolvesToEmpty() {
        assertThat(index.byName("   ")).isEmpty();
    }

    @Test
    void recordPersistsThroughTheRepository() {
        UUID player = UUID.randomUUID();
        index.backWith(repository, 100);

        index.record(player, "Cofteey");

        assertThat(repository.upserted).singleElement().satisfies(row -> {
            assertThat(row.uuid()).isEqualTo(player);
            assertThat(row.name()).isEqualTo("Cofteey");
        });
    }

    @Test
    void recordWithoutABackingRepositoryStaysInMemory() {
        UUID player = UUID.randomUUID();

        index.record(player, "Cofteey");

        assertThat(index.byName("cofteey")).contains(new PlayerRef(player, "Cofteey"));
        assertThat(repository.upserted).isEmpty();
    }

    @Test
    void backWithWarmsTheMapFromTheRepository() {
        UUID player = UUID.randomUUID();
        repository.rows.add(new PlayerName(player, "Warmed", 1_000L));

        index.backWith(repository, 100);

        assertThat(index.byName("warmed")).contains(new PlayerRef(player, "Warmed"));
    }

    @Test
    void theNewestAccountWinsWhenTwoShareALowerCasedName() {
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        repository.rows.add(new PlayerName(newer, "cofteey", 2_000L));
        repository.rows.add(new PlayerName(older, "Cofteey", 1_000L));

        index.backWith(repository, 100);

        assertThat(index.byName("COFTEEY").orElseThrow().uuid()).isEqualTo(newer);
    }

    @Test
    void aLaterRecordReplacesTheEntryForThatName() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        index.record(first, "Cofteey");

        index.record(second, "cofteey");

        assertThat(index.byName("Cofteey").orElseThrow().uuid()).isEqualTo(second);
    }

    @Test
    void aBlankNameIsNeverRecorded() {
        index.backWith(repository, 100);

        index.record(UUID.randomUUID(), "   ");

        assertThat(repository.upserted).isEmpty();
    }

    /** A repository that serves the warm from {@link #rows} newest first and collects every write. */
    private static final class RecordingRepository implements PlayerNameRepository {

        private final List<PlayerName> rows = new ArrayList<>();
        private final List<PlayerName> upserted = new ArrayList<>();

        @Override
        public List<PlayerName> loadRecent(int limit) {
            return rows.stream()
                    .sorted(Comparator.comparingLong(PlayerName::lastSeen).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public void upsert(PlayerName record) {
            upserted.add(record);
        }

        @Override
        public int count() {
            return rows.size();
        }
    }

    private static final class ImmediateScheduler implements Scheduler {
        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
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
