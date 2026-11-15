package com.uxplima.uxmessentials.persistence.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.staff.domain.LoadoutBlob;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/** Loadouts saved before the server id was read correctly, upgraded and adopted through the real migrations. */
class JooqStaffLoadoutRepositoryTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final SavedLoadout SURVIVAL_KIT = loadout(1, "SURVIVAL");
    private static final SavedLoadout CREATIVE_KIT = loadout(2, "CREATIVE");

    @TempDir
    Path folder;

    private SQLiteDataSource data;
    private DSLContext dsl;

    /** A database as the release before the unscoped marking left it. */
    @BeforeEach
    void open() {
        data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        Flyway.configure()
                .dataSource(data)
                .locations("classpath:db/migration")
                .placeholders(Map.of("prefix", ""))
                .target("88")
                .load()
                .migrate();
        dsl = DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), "");
    }

    private void upgrade() {
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
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
    void aRowSavedUnderTheDefaultIdAfterTheUpgradeIsNeverAdopted() {
        upgrade();
        JooqStaffLoadoutRepository defaultNamed = on("server-1");
        JooqStaffLoadoutRepository lobby = on("lobby");
        defaultNamed.save(ALEX, SURVIVAL_KIT);

        StaffLoadoutAdoption adoption = lobby.adopt();

        assertThat(adoption).isEqualTo(new StaffLoadoutAdoption(0, List.of()));
        assertThat(defaultNamed.load(ALEX)).contains(SURVIVAL_KIT);
        assertThat(lobby.load(ALEX)).isEmpty();
    }

    @Test
    void upgradedRowsAreAdoptedAndOneAlreadyHeldHereIsReported() {
        JooqStaffLoadoutRepository oldRelease = on("server-1");
        oldRelease.save(ALEX, SURVIVAL_KIT);
        oldRelease.save(SAM, SURVIVAL_KIT);
        upgrade();
        JooqStaffLoadoutRepository lobby = on("lobby");
        lobby.save(SAM, CREATIVE_KIT);

        StaffLoadoutAdoption adoption = lobby.adopt();

        assertThat(adoption).isEqualTo(new StaffLoadoutAdoption(1, List.of(SAM)));
        assertThat(lobby.load(ALEX)).contains(SURVIVAL_KIT);
        assertThat(lobby.load(SAM)).contains(CREATIVE_KIT);
        assertThat(oldRelease.load(ALEX)).isEmpty();
    }
}
