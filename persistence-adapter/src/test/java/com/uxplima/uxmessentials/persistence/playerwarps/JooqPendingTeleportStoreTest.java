package com.uxplima.uxmessentials.persistence.playerwarps;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleport;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleportStore;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/** The pending cross-server teleport row through the real migrations on a SQLite file. */
class JooqPendingTeleportStoreTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000a1e");
    private static final Instant ASKED = Instant.parse("2030-01-01T12:00:00.123456789Z");

    @TempDir
    Path folder;

    private PendingTeleportStore store;

    @BeforeEach
    void open() {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
        store = new JooqPendingTeleportStore(DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), ""));
    }

    private static PendingTeleport request(Instant at) {
        return new PendingTeleport(
                ALEX,
                PlayerWarpId.of(7),
                "survival",
                "lobby",
                at,
                Optional.of(WarpCost.of(new BigDecimal("25.5000"), "gold")),
                true);
    }

    @Test
    void whetherTheArrivalPaysTheOwnerSurvivesTheRoundTrip() {
        store.record(request(ASKED));
        assertThat(store.find(ALEX).map(PendingTeleport::accrueOnArrival)).contains(true);

        PendingTeleport earlier =
                new PendingTeleport(ALEX, PlayerWarpId.of(7), "survival", "lobby", ASKED, Optional.empty(), false);
        store.record(earlier);
        assertThat(store.find(ALEX).map(PendingTeleport::accrueOnArrival)).contains(false);
    }

    @Test
    void onlyTheFirstClearOfARequestRemovesIt() {
        store.record(request(ASKED));

        assertThat(store.clear(request(ASKED))).isEqualTo(1);
        assertThat(store.clear(request(ASKED))).isZero();
        assertThat(store.find(ALEX)).isEmpty();
    }

    @Test
    void theRowReadBackClearsTheRequestItWasRecordedFrom() {
        store.record(request(ASKED));

        PendingTeleport found = store.find(ALEX).orElseThrow();

        assertThat(found.requestedAt()).isEqualTo(Instant.ofEpochMilli(ASKED.toEpochMilli()));
        assertThat(store.clear(found)).isEqualTo(1);
        assertThat(store.find(ALEX)).isEmpty();
    }

    @Test
    void aClearForAnOlderRequestLeavesTheNewerOneInPlace() {
        store.record(request(ASKED));
        PendingTeleport newer = request(ASKED.plusSeconds(5));
        store.record(newer);

        assertThat(store.clear(request(ASKED))).isZero();
        assertThat(store.find(ALEX).map(PendingTeleport::requestedAt))
                .contains(Instant.ofEpochMilli(newer.requestedAt().toEpochMilli()));
    }
}
