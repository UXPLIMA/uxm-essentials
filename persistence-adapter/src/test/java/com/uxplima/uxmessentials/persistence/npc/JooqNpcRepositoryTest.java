package com.uxplima.uxmessentials.persistence.npc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqNpcRepository} against the default embedded SQLite backend with the Flyway
 * V38 baseline applied. It proves the round-trip (save → find) of the row including a skin and a click command,
 * the null skin / null command path, the name-key upsert (a move/re-skin overwrites in place), the delete, the
 * {@code exists} check, and the creation-order list.
 */
class JooqNpcRepositoryTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private Persistence persistence;
    private JooqNpcRepository repository;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqNpcRepository(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void savesAndFindsAnNpcWithSkinAndCommand() {
        repository.save(new Npc(
                NpcName.of("guide"),
                Position.of(WORLD, 10, 64, 20),
                new NpcSkin("tex", "sig"),
                "warp spawn",
                Instant.ofEpochMilli(1_000)));

        Optional<Npc> loaded = repository.find(NpcName.of("guide"));

        assertThat(loaded).isPresent();
        Npc reloaded = loaded.orElseThrow();
        assertThat(reloaded.location().blockX()).isEqualTo(10);
        assertThat(reloaded.location().blockZ()).isEqualTo(20);
        assertThat(reloaded.location().world().name()).isEqualTo("world");
        assertThat(reloaded.skin()).isEqualTo(new NpcSkin("tex", "sig"));
        assertThat(reloaded.clickCommand()).isEqualTo("warp spawn");
    }

    @Test
    void savesAndFindsAnNpcWithNoSkinOrCommand() {
        repository.save(
                Npc.create(NpcName.of("plain"), Position.of(WORLD, 0, 64, 0), null, Instant.ofEpochMilli(1_000)));

        Npc loaded = repository.find(NpcName.of("plain")).orElseThrow();

        assertThat(loaded.skin()).isNull();
        assertThat(loaded.clickCommand()).isNull();
        assertThat(loaded.hasSkin()).isFalse();
    }

    @Test
    void roundTripsAnUnsignedSkin() {
        repository.save(Npc.create(
                NpcName.of("guide"),
                Position.of(WORLD, 1, 64, 1),
                NpcSkin.unsigned("tex"),
                Instant.ofEpochMilli(1_000)));

        Npc loaded = repository.find(NpcName.of("guide")).orElseThrow();

        assertThat(loaded.skin()).isEqualTo(NpcSkin.unsigned("tex"));
    }

    @Test
    void saveUpsertsOnTheNameKey() {
        repository.save(npc("guide", 0, 0, 0));
        repository.save(new Npc(
                NpcName.of("guide"),
                Position.of(WORLD, 100, 70, 100),
                new NpcSkin("tex2", null),
                "spawn",
                Instant.ofEpochMilli(1_000)));

        assertThat(repository.all()).hasSize(1);
        Npc updated = repository.find(NpcName.of("guide")).orElseThrow();
        assertThat(updated.location().blockX()).isEqualTo(100);
        assertThat(updated.skin()).isEqualTo(new NpcSkin("tex2", null));
        assertThat(updated.clickCommand()).isEqualTo("spawn");
    }

    @Test
    void existsReflectsWhetherAnNpcIsStored() {
        assertThat(repository.exists(NpcName.of("guide"))).isFalse();

        repository.save(npc("guide", 0, 0, 0));

        assertThat(repository.exists(NpcName.of("guide"))).isTrue();
    }

    @Test
    void deleteRemovesTheRow() {
        repository.save(npc("guide", 0, 0, 0));
        repository.save(npc("shop", 1, 1, 1));

        repository.delete(NpcName.of("guide"));

        assertThat(repository.exists(NpcName.of("guide"))).isFalse();
        assertThat(repository.all()).hasSize(1);
    }

    @Test
    void allPreservesCreationOrder() {
        repository.save(npcAt("first", Instant.ofEpochMilli(1_000)));
        repository.save(npcAt("second", Instant.ofEpochMilli(2_000)));
        repository.save(npcAt("third", Instant.ofEpochMilli(3_000)));

        assertThat(repository.all().stream().map(n -> n.name().value())).containsExactly("first", "second", "third");
    }

    private Npc npc(String name, double x, double y, double z) {
        return Npc.create(NpcName.of(name), Position.of(WORLD, x, y, z), null, Instant.ofEpochMilli(1_000));
    }

    private Npc npcAt(String name, Instant createdAt) {
        return Npc.create(NpcName.of(name), Position.of(WORLD, 0, 64, 0), null, createdAt);
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
