package com.uxplima.uxmessentials.persistence.vanish;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.vanish.application.port.VanishRecords;
import com.uxplima.uxmessentials.vanish.domain.StoredVanish;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/** Each player's kept vanish through the real migrations on a SQLite file. */
class JooqVanishRecordsTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final Instant EARLY = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant LATE = Instant.parse("2030-01-01T00:00:05Z");

    @TempDir
    Path folder;

    private VanishRecords records;

    @BeforeEach
    void open() {
        records = openStore();
    }

    @Test
    void aKeptVanishIsReadBackByAnotherConnection() {
        records.save(ALEX, StoredVanish.vanished(VanishLevel.of(3), "survival", EARLY));

        assertThat(openStore().find(ALEX)).contains(StoredVanish.vanished(VanishLevel.of(3), "survival", EARLY));
        assertThat(openStore().find(SAM)).isEmpty();
    }

    @Test
    void aLaterRevealReplacesTheVanish() {
        records.save(ALEX, StoredVanish.vanished(VanishLevel.DEFAULT, "survival", EARLY));
        records.save(ALEX, StoredVanish.visible(LATE));

        assertThat(records.find(ALEX)).contains(StoredVanish.visible(LATE));
    }

    @Test
    void aWriteThatLandsAfterANewerOneIsDropped() {
        records.save(ALEX, StoredVanish.visible(LATE));
        records.save(ALEX, StoredVanish.vanished(VanishLevel.of(2), "survival", EARLY));

        assertThat(records.find(ALEX)).isEqualTo(Optional.of(StoredVanish.visible(LATE)));
    }

    @Test
    void aDepartureIsKeptOnlyFromTheServerThePlayerIsOnAndClearedByTheirArrival() {
        records.save(ALEX, StoredVanish.vanished(VanishLevel.of(2), "survival", EARLY));
        records.markDeparted(ALEX, "lobby", LATE);
        assertThat(records.find(ALEX)).contains(StoredVanish.vanished(VanishLevel.of(2), "survival", EARLY));

        records.markDeparted(ALEX, "survival", LATE);
        assertThat(openStore().find(ALEX))
                .contains(StoredVanish.vanished(VanishLevel.of(2), "survival", EARLY)
                        .departed(LATE));

        records.markArrived(ALEX, "lobby");
        assertThat(records.find(ALEX)).contains(StoredVanish.vanished(VanishLevel.of(2), "lobby", EARLY));
    }

    @Test
    void aDepartureOrArrivalOnAClearedRowChangesNothing() {
        records.save(ALEX, StoredVanish.visible(EARLY));
        records.markDeparted(ALEX, "survival", LATE);
        records.markArrived(ALEX, "lobby");

        assertThat(records.find(ALEX)).contains(StoredVanish.visible(EARLY));
    }

    private VanishRecords openStore() {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
        DSLContext dsl = DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), "");
        return new JooqVanishRecords(dsl);
    }
}
