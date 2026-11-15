package com.uxplima.uxmessentials.persistence.playerwarps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.shared.application.hop.HopCharge;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.hop.HopSettlement;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHopStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/** The pending cross-server hop row through the real migrations on a SQLite file. */
class JooqPendingHopStoreTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000a1e");
    private static final Instant ASKED = Instant.parse("2030-01-01T12:00:00.123456789Z");
    private static final Optional<HopSettlement> TO_THE_WARP = Optional.of(new HopSettlement("7"));

    @TempDir
    Path folder;

    private PendingHopStore store;

    @BeforeEach
    void open() {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
        store = new JooqPendingHopStore(DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), ""));
    }

    private static PendingHop request(Instant at) {
        return hop(HopKind.PLAYER_WARP, at, TO_THE_WARP);
    }

    private static PendingHop hop(HopKind kind, Instant at, Optional<HopSettlement> settlement) {
        return new PendingHop(
                ALEX,
                kind,
                "7",
                "survival",
                "lobby",
                at,
                Optional.of(new HopCharge(new BigDecimal("25.5000"), "gold")),
                settlement);
    }

    @Test
    void theSettlementSurvivesTheRoundTrip() {
        store.record(request(ASKED));
        assertThat(store.find(ALEX).flatMap(PendingHop::settlement)).isEqualTo(TO_THE_WARP);

        store.record(hop(HopKind.PLAYER_WARP, ASKED, Optional.empty()));
        assertThat(store.find(ALEX).flatMap(PendingHop::settlement)).isEmpty();
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

        PendingHop found = store.find(ALEX).orElseThrow();

        assertThat(found.kind()).isEqualTo(HopKind.PLAYER_WARP);
        HopCharge charge = found.charge().orElseThrow();
        assertThat(charge.amount()).isEqualByComparingTo("25.5");
        assertThat(charge.currencyId()).isEqualTo("gold");
        assertThat(found.requestedAt()).isEqualTo(Instant.ofEpochMilli(ASKED.toEpochMilli()));
        assertThat(store.clear(found)).isEqualTo(1);
        assertThat(store.find(ALEX)).isEmpty();
    }

    @Test
    void aClearForAnOlderRequestLeavesTheNewerOneInPlace() {
        store.record(request(ASKED));
        PendingHop newer = request(ASKED.plusSeconds(5));
        store.record(newer);

        assertThat(store.clear(request(ASKED))).isZero();
        assertThat(store.find(ALEX).map(PendingHop::requestedAt))
                .contains(Instant.ofEpochMilli(newer.requestedAt().toEpochMilli()));
    }

    @Test
    void aHopOfAKindTheTableCannotHoldIsRefusedAndLeavesThePlayerWarpRow() {
        store.record(request(ASKED));

        assertThatThrownBy(() -> store.record(hop(HopKind.HOME, ASKED.plusSeconds(1), Optional.empty())))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(store.clear(hop(HopKind.HOME, ASKED, TO_THE_WARP))).isZero();
        assertThat(store.find(ALEX).map(PendingHop::kind)).contains(HopKind.PLAYER_WARP);
    }
}
