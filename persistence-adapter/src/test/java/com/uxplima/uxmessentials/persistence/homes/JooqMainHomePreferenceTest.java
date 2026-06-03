package com.uxplima.uxmessentials.persistence.homes;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqMainHomePreference} against the default embedded SQLite backend with
 * the Flyway V1-V9 migrations applied. It proves the set → read round-trip, the {@code owner}-keyed upsert
 * (re-choosing overwrites in place rather than inserting a second row), the clear, and that an owner with
 * no row reads as empty (the never-chose-one fallback the resolver relies on).
 */
class JooqMainHomePreferenceTest {

    private Persistence persistence;
    private JooqMainHomePreference preferences;
    private PlayerRef owner;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        preferences = new JooqMainHomePreference(persistence.dsl());
        owner = new PlayerRef(UUID.randomUUID(), "Alice");
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void absentOwnerReadsAsEmpty() {
        assertThat(preferences.mainHomeOf(owner)).isEmpty();
    }

    @Test
    void setThenReadRoundTrips() {
        preferences.setMainHome(owner, HomeName.of("base"));

        assertThat(preferences.mainHomeOf(owner)).contains(HomeName.of("base"));
    }

    @Test
    void reChoosingOverwritesTheSameRow() {
        preferences.setMainHome(owner, HomeName.of("base"));
        preferences.setMainHome(owner, HomeName.of("camp"));

        assertThat(preferences.mainHomeOf(owner)).contains(HomeName.of("camp"));
    }

    @Test
    void clearRemovesTheChoice() {
        preferences.setMainHome(owner, HomeName.of("base"));

        preferences.clear(owner);

        assertThat(preferences.mainHomeOf(owner)).isEmpty();
    }

    /** A config that selects the embedded SQLite backend with every default — no network coordinates. */
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
