package com.uxplima.uxmessentials.persistence.runtime;

import static com.uxplima.uxmessentials.persistence.jooq.tables.HologramLines.HOLOGRAM_LINES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.Holograms.HOLOGRAMS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.Npc.NPC;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.health.HealthStatus;
import com.uxplima.uxmessentials.shared.application.health.RepairStatus;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceIntegrityHealthCheckTest {

    private Persistence persistence;
    private PersistenceIntegrityHealthCheck check;
    private Path worldContainer;

    @BeforeEach
    void setUp(@TempDir Path temp) throws Exception {
        Path data = temp.resolve("plugin");
        worldContainer = temp.resolve("server");
        Files.createDirectories(worldContainer.resolve("world"));
        persistence = Persistence.open(new SqliteConfig(), data, List.of("db/migration"), new NoopLogger());
        check = new PersistenceIntegrityHealthCheck(persistence.dsl(), worldContainer);
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void reportsAndTransactionallyRemovesOnlyUnambiguousOrphans() {
        insertHologram("kept", "world", null);
        persistence
                .dsl()
                .transaction(configuration -> configuration
                        .dsl()
                        .insertInto(HOLOGRAM_LINES)
                        .set(HOLOGRAM_LINES.HOLOGRAM, "missing")
                        .set(HOLOGRAM_LINES.IDX, 0)
                        .set(HOLOGRAM_LINES.TEXT, "orphan")
                        .execute());

        assertThat(check.check().status()).isEqualTo(HealthStatus.WARN);
        assertThat(check.check().message()).contains("orphanRows=1");

        var repaired = check.repair();

        assertThat(repaired.status()).isEqualTo(RepairStatus.REPAIRED);
        assertThat(repaired.message()).contains("changedRows=1");
        assertThat(persistence.dsl().fetchCount(HOLOGRAM_LINES)).isZero();
        assertThat(persistence.dsl().fetchCount(HOLOGRAMS)).isEqualTo(1);
        assertThat(check.repair().status()).isEqualTo(RepairStatus.UNCHANGED);
    }

    @Test
    void clearsADanglingNpcLinkButRetainsTheHologram() {
        insertHologram("linked", "world", "deleted-npc");

        assertThat(check.check().message()).contains("danglingLinks=1");

        assertThat(check.repair().status()).isEqualTo(RepairStatus.REPAIRED);
        assertThat(persistence
                        .dsl()
                        .select(HOLOGRAMS.LINKED_NPC_NAME)
                        .from(HOLOGRAMS)
                        .fetchOne(0, String.class))
                .isNull();
        assertThat(persistence.dsl().fetchCount(HOLOGRAMS)).isEqualTo(1);
        assertThat(persistence.dsl().fetchCount(NPC)).isZero();
    }

    @Test
    void missingWorldRecordsAreReportedAndNeverDeletedByRepair() {
        insertHologram("recoverable", "restorable_world", null);

        assertThat(check.check().message()).contains("missingWorlds=restorable_world");

        assertThat(check.repair().status()).isEqualTo(RepairStatus.UNCHANGED);
        assertThat(check.repair().message()).contains("retained location records");
        assertThat(persistence.dsl().fetchCount(HOLOGRAMS)).isEqualTo(1);
    }

    private void insertHologram(String name, String world, @org.jspecify.annotations.Nullable String linkedNpc) {
        persistence
                .dsl()
                .transaction(configuration -> configuration
                        .dsl()
                        .insertInto(HOLOGRAMS)
                        .set(HOLOGRAMS.NAME, name)
                        .set(HOLOGRAMS.WORLD, UUID.randomUUID().toString())
                        .set(HOLOGRAMS.WORLD_NAME, world)
                        .set(HOLOGRAMS.X, 0.0)
                        .set(HOLOGRAMS.Y, 64.0)
                        .set(HOLOGRAMS.Z, 0.0)
                        .set(HOLOGRAMS.YAW, 0.0f)
                        .set(HOLOGRAMS.PITCH, 0.0f)
                        .set(HOLOGRAMS.CREATED_AT, 1L)
                        .set(HOLOGRAMS.LINKED_NPC_NAME, linkedNpc)
                        .execute());
    }

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
