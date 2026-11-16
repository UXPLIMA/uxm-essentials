package com.uxplima.uxmessentials.persistence.teleport;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.teleport.application.port.PendingRelocations;
import com.uxplima.uxmessentials.teleport.domain.PendingRelocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/** The {@code /tpofflinehere} relocation row through the real migrations on a SQLite file. */
class JooqPendingRelocationsTest {

    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000a1e");
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");
    private static final Instant SET = Instant.parse("2030-01-01T12:00:00.123456Z");

    @TempDir
    Path folder;

    private PendingRelocations store;

    @BeforeEach
    void open() {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
        store = new JooqPendingRelocations(DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), ""));
    }

    private static PendingRelocation to(String server, double x, Instant at) {
        return new PendingRelocation(SAM, server, WORLD, x, 64.5, -3.25, 90f, -12.5f, at, ALEX, 0);
    }

    @Test
    void aRelocationSurvivesTheRoundTripPerServer() {
        store.save(to("survival", 10.5, SET));
        store.save(to("lobby", 99.5, SET));

        assertThat(store.find(SAM, "survival")).contains(to("survival", 10.5, SET));
        assertThat(store.find(SAM, "lobby")).contains(to("lobby", 99.5, SET));
        assertThat(store.find(ALEX, "survival")).isEmpty();
    }

    @Test
    void aLaterSaveReplacesTheEarlierOneOnThatServer() {
        store.save(to("survival", 10.5, SET));
        store.save(to("survival", 20.5, SET.plusSeconds(5)));

        assertThat(store.find(SAM, "survival")).contains(to("survival", 20.5, SET.plusSeconds(5)));
    }

    @Test
    void onlyTheFirstClaimRemovesItAndAReplacedRowIsNotClaimed() {
        store.save(to("survival", 10.5, SET));

        assertThat(store.claim(to("survival", 10.5, SET))).isTrue();
        assertThat(store.claim(to("survival", 10.5, SET))).isFalse();

        store.save(to("survival", 20.5, SET.plusSeconds(5)));
        assertThat(store.claim(to("survival", 10.5, SET))).isFalse();
        assertThat(store.find(SAM, "survival")).isPresent();
    }

    @Test
    void aRestoreNeverOverwritesANewerRelocation() {
        store.save(to("survival", 10.5, SET));
        store.claim(to("survival", 10.5, SET));
        store.restore(to("survival", 10.5, SET));
        assertThat(store.find(SAM, "survival")).contains(to("survival", 10.5, SET));

        store.save(to("survival", 20.5, SET.plusSeconds(5)));
        store.restore(to("survival", 10.5, SET));
        assertThat(store.find(SAM, "survival")).contains(to("survival", 20.5, SET.plusSeconds(5)));
    }

    @Test
    void aRestoredRowKeepsItsCountOfMisses() {
        PendingRelocation missedTwice = to("survival", 10.5, SET).missedOnce().missedOnce();
        store.save(to("survival", 10.5, SET));
        store.claim(to("survival", 10.5, SET));

        store.restore(missedTwice);

        assertThat(store.find(SAM, "survival")).contains(missedTwice);
    }
}
