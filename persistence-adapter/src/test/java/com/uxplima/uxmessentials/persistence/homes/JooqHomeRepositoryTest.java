package com.uxplima.uxmessentials.persistence.homes;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqHomeRepository} against the default embedded SQLite backend with the
 * Flyway V1 baseline applied — the tested default of the backend-parity matrix (network backends run the
 * same DSL behind Testcontainers, which this environment may not have). It proves the round-trip
 * (save → load), the {@code (owner, name)} upsert (a re-anchor overwrites in place rather than inserting),
 * the keyed rename, the delete, and the {@code COUNT(*)} the quota check relies on.
 */
class JooqHomeRepositoryTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private Persistence persistence;
    private JooqHomeRepository repository;
    private PlayerRef owner;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqHomeRepository(persistence.dsl());
        owner = new PlayerRef(UUID.randomUUID(), "Alice");
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void savesAndLoadsAHomeRoundTrip() {
        repository.save(home("base", 10, 64, 20));

        HomeSet loaded = repository.load(owner);

        assertThat(loaded.count()).isEqualTo(1);
        Home reloaded = loaded.find(HomeName.of("base")).orElseThrow();
        assertThat(reloaded.location().blockX()).isEqualTo(10);
        assertThat(reloaded.location().blockZ()).isEqualTo(20);
        assertThat(reloaded.location().world().name()).isEqualTo("world");
    }

    @Test
    void saveUpsertsOnTheOwnerNameKeyRatherThanInserting() {
        repository.save(home("base", 0, 0, 0));
        repository.save(home("base", 100, 70, 100)); // same (owner, name) — a re-anchor

        assertThat(repository.count(owner)).isEqualTo(1);
        Home reanchored = repository.load(owner).find(HomeName.of("base")).orElseThrow();
        assertThat(reanchored.location().blockX()).isEqualTo(100);
    }

    @Test
    void countReflectsTheNumberOfHomes() {
        repository.save(home("a", 0, 0, 0));
        repository.save(home("b", 1, 1, 1));
        repository.save(home("c", 2, 2, 2));

        assertThat(repository.count(owner)).isEqualTo(3);
    }

    @Test
    void renameMovesTheRowToTheNewKey() {
        repository.save(home("base", 5, 5, 5));

        repository.rename(owner, HomeName.of("base"), HomeName.of("camp"));

        HomeSet loaded = repository.load(owner);
        assertThat(loaded.find(HomeName.of("base"))).isEmpty();
        assertThat(loaded.find(HomeName.of("camp"))).isPresent();
        assertThat(loaded.find(HomeName.of("camp")).orElseThrow().location().blockX())
                .isEqualTo(5);
    }

    @Test
    void deleteRemovesTheRow() {
        repository.save(home("base", 0, 0, 0));
        repository.save(home("camp", 1, 1, 1));

        repository.delete(owner, HomeName.of("base"));

        assertThat(repository.count(owner)).isEqualTo(1);
        assertThat(repository.load(owner).find(HomeName.of("base"))).isEmpty();
    }

    @Test
    void loadPreservesCreationOrder() {
        repository.save(homeAt("first", 0, Instant.ofEpochMilli(1_000)));
        repository.save(homeAt("second", 1, Instant.ofEpochMilli(2_000)));
        repository.save(homeAt("third", 2, Instant.ofEpochMilli(3_000)));

        assertThat(repository.load(owner).all().stream().map(h -> h.name().value()))
                .containsExactly("first", "second", "third");
    }

    private Home home(String name, double x, double y, double z) {
        return new Home(owner, HomeName.of(name), Position.of(WORLD, x, y, z), Instant.ofEpochMilli(1_000));
    }

    private Home homeAt(String name, double x, Instant createdAt) {
        return new Home(owner, HomeName.of(name), Position.of(WORLD, x, 64, x), createdAt);
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
