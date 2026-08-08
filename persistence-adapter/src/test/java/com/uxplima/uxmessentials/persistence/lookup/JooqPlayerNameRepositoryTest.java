package com.uxplima.uxmessentials.persistence.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqPlayerNameRepository} against the default embedded SQLite backend with the
 * Flyway ladder applied. It proves the upsert keys on the account rather than the name, that the read returns the
 * most recently seen rows first and honours its limit, and that two accounts may legitimately share one
 * lower-cased name (a name change on an online-mode server, two case variants on an offline-mode one).
 */
class JooqPlayerNameRepositoryTest {

    private Persistence persistence;
    private JooqPlayerNameRepository repository;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqPlayerNameRepository(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void upsertThenLoadRecentReturnsTheRow() {
        UUID player = UUID.randomUUID();

        repository.upsert(new PlayerName(player, "Cofteey", 1_000L));

        assertThat(repository.loadRecent(10)).containsExactly(new PlayerName(player, "Cofteey", 1_000L));
    }

    @Test
    void upsertOnTheSameAccountReplacesTheNameAndKeepsOneRow() {
        UUID player = UUID.randomUUID();
        repository.upsert(new PlayerName(player, "OldName", 1_000L));

        repository.upsert(new PlayerName(player, "NewName", 2_000L));

        assertThat(repository.loadRecent(10)).containsExactly(new PlayerName(player, "NewName", 2_000L));
    }

    @Test
    void loadRecentReturnsNewestFirstAndHonoursTheLimit() {
        repository.upsert(new PlayerName(UUID.randomUUID(), "Oldest", 1_000L));
        repository.upsert(new PlayerName(UUID.randomUUID(), "Newest", 3_000L));
        repository.upsert(new PlayerName(UUID.randomUUID(), "Middle", 2_000L));

        assertThat(repository.loadRecent(2)).extracting(PlayerName::name).containsExactly("Newest", "Middle");
    }

    @Test
    void loadRecentOfANonPositiveLimitIsEmpty() {
        repository.upsert(new PlayerName(UUID.randomUUID(), "Cofteey", 1_000L));

        assertThat(repository.loadRecent(0)).isEmpty();
    }

    @Test
    void twoAccountsMayShareOneLowerCasedName() {
        repository.upsert(new PlayerName(UUID.randomUUID(), "Cofteey", 1_000L));
        repository.upsert(new PlayerName(UUID.randomUUID(), "cofteey", 2_000L));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void countIsZeroOnAnEmptyTable() {
        assertThat(repository.count()).isZero();
    }

    /** A config that selects the embedded SQLite backend with every default, no network coordinates. */
    private record SqliteConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
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
