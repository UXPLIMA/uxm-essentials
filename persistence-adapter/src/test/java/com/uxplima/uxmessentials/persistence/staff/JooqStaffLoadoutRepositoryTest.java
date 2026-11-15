package com.uxplima.uxmessentials.persistence.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.staff.domain.LoadoutBlob;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/** Loadouts kept per backend, and moved off the default server id, through the real migrations on SQLite. */
class JooqStaffLoadoutRepositoryTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final SavedLoadout SURVIVAL_KIT = loadout(1, "SURVIVAL");
    private static final SavedLoadout CREATIVE_KIT = loadout(2, "CREATIVE");

    @TempDir
    Path folder;

    private DSLContext dsl;

    @BeforeEach
    void open() {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
        dsl = DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), "");
    }

    private JooqStaffLoadoutRepository on(String serverId) {
        return new JooqStaffLoadoutRepository(
                dsl, Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC), serverId);
    }

    private static SavedLoadout loadout(int marker, String gameMode) {
        return new SavedLoadout(
                LoadoutBlob.of(new byte[] {(byte) marker, 7}),
                LoadoutBlob.of(new byte[] {(byte) marker}),
                LoadoutBlob.empty(),
                marker,
                marker * 10,
                0.5f,
                gameMode,
                false,
                true,
                LoadoutBlob.empty(),
                marker % 2 == 0);
    }

    @Test
    void backendsSharingOneDatabaseKeepTheirOwnLoadout() {
        JooqStaffLoadoutRepository lobby = on("lobby");
        JooqStaffLoadoutRepository survival = on("survival");

        lobby.save(ALEX, SURVIVAL_KIT);
        survival.save(ALEX, CREATIVE_KIT);
        survival.delete(ALEX);

        assertThat(lobby.load(ALEX)).contains(SURVIVAL_KIT);
        assertThat(survival.load(ALEX)).isEmpty();
    }

    @Test
    void adoptMovesDefaultIdRowsAndKeepsALoadoutAlreadyHeldHere() {
        JooqStaffLoadoutRepository legacy = on("server-1");
        JooqStaffLoadoutRepository lobby = on("lobby");
        legacy.save(ALEX, SURVIVAL_KIT);
        legacy.save(SAM, SURVIVAL_KIT);
        lobby.save(SAM, CREATIVE_KIT);

        int moved = lobby.adopt("server-1");

        assertThat(moved).isEqualTo(1);
        assertThat(lobby.load(ALEX)).contains(SURVIVAL_KIT);
        assertThat(legacy.load(ALEX)).isEmpty();
        assertThat(lobby.load(SAM)).contains(CREATIVE_KIT);
        assertThat(legacy.load(SAM)).contains(SURVIVAL_KIT);
    }
}
