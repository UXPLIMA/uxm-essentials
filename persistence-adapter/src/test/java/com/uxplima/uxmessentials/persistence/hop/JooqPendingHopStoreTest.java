package com.uxplima.uxmessentials.persistence.hop;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpPendingTeleports.PLAYER_WARP_PENDING_TELEPORTS;
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
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/** The pending cross-server hop row of every kind through the real migrations on a SQLite file. */
class JooqPendingHopStoreTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000a1e");
    private static final Instant ASKED = Instant.parse("2030-01-01T12:00:00.123456789Z");
    private static final Optional<HopSettlement> TO_THE_WARP = Optional.of(new HopSettlement("7"));

    @TempDir
    Path folder;

    private PendingHopStore store;
    private DSLContext dsl;

    @BeforeEach
    void open() {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
        dsl = DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), "");
        store = new JooqPendingHopStore(dsl);
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
    void aHopOfAnotherKindSurvivesTheRoundTripAndIsClaimedOnlyAsItself() {
        PendingHop toMarket = new PendingHop(
                ALEX,
                HopKind.WARP,
                "market",
                "survival",
                "lobby",
                ASKED,
                Optional.of(new HopCharge(new BigDecimal("3.0000"), "gold")),
                Optional.of(new HopSettlement("keeper")));
        store.record(toMarket);

        PendingHop found = store.find(ALEX).orElseThrow();

        assertThat(found.kind()).isEqualTo(HopKind.WARP);
        assertThat(found.target()).isEqualTo("market");
        assertThat(found.settlement()).contains(new HopSettlement("keeper"));
        assertThat(found.charge().orElseThrow().amount()).isEqualByComparingTo("3");
        assertThat(store.clear(hop(HopKind.HOME, ASKED, Optional.empty()))).isZero();
        assertThat(store.clear(request(ASKED))).isZero();
        assertThat(store.clear(found)).isEqualTo(1);
    }

    @Test
    void aHopOfAnotherKindKeepsAWarpIdNoPlayerWarpHasSoAnOlderBackendCannotTakeIt() {
        store.record(new PendingHop(
                ALEX, HopKind.WARP, "market", "survival", "lobby", ASKED, Optional.empty(), Optional.empty()));

        var row = dsl.selectFrom(PLAYER_WARP_PENDING_TELEPORTS).fetchSingle();

        assertThat(row.getWarpId()).isZero();
        assertThat(row.getKind()).isEqualTo("WARP");
    }

    @Test
    void aPlayerWarpHopIsWrittenInTheShapeAnOlderBackendReads() {
        store.record(request(ASKED));

        var row = dsl.selectFrom(PLAYER_WARP_PENDING_TELEPORTS).fetchSingle();

        assertThat(row.getKind()).isNull();
        assertThat(row.getTargetRef()).isNull();
        assertThat(row.getWarpId()).isEqualTo(7L);
        assertThat(row.getAccrueOnArrival()).isEqualTo((short) 1);
    }

    @Test
    void aRowAnOlderReleaseWroteStillResolvesAsAPlayerWarpHop() {
        dsl.insertInto(PLAYER_WARP_PENDING_TELEPORTS)
                .set(PLAYER_WARP_PENDING_TELEPORTS.PLAYER_UUID, ALEX.toString())
                .set(PLAYER_WARP_PENDING_TELEPORTS.WARP_ID, 7L)
                .set(PLAYER_WARP_PENDING_TELEPORTS.TARGET_SERVER, "survival")
                .set(PLAYER_WARP_PENDING_TELEPORTS.ORIGIN_SERVER, "lobby")
                .set(PLAYER_WARP_PENDING_TELEPORTS.REQUESTED_AT, ASKED.toEpochMilli())
                .set(PLAYER_WARP_PENDING_TELEPORTS.PAID_AMOUNT, new BigDecimal("25.5000"))
                .set(PLAYER_WARP_PENDING_TELEPORTS.PAID_CURRENCY, "gold")
                .execute();

        PendingHop found = store.find(ALEX).orElseThrow();

        assertThat(found.kind()).isEqualTo(HopKind.PLAYER_WARP);
        assertThat(found.target()).isEqualTo("7");
        assertThat(found.settlement()).isEmpty();
        assertThat(found.charge().orElseThrow().amount()).isEqualByComparingTo("25.5");
        assertThat(store.clear(found)).isEqualTo(1);
    }

    @Test
    void aPlayerWarpHopThatSettlesToAnotherWarpIsRefused() {
        assertThatThrownBy(() -> store.record(hop(HopKind.PLAYER_WARP, ASKED, Optional.of(new HopSettlement("8")))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(store.find(ALEX)).isEmpty();
    }

    @Test
    void aRowAnOlderBackendRewroteOverAnotherKindIsAPlayerWarpHop() {
        store.record(new PendingHop(
                ALEX, HopKind.WARP, "market", "survival", "lobby", ASKED, Optional.empty(), Optional.empty()));
        // What an older backend's upsert of a player-warp hop leaves: its own columns, and the stale kind beside them.
        dsl.update(PLAYER_WARP_PENDING_TELEPORTS)
                .set(PLAYER_WARP_PENDING_TELEPORTS.WARP_ID, 7L)
                .set(
                        PLAYER_WARP_PENDING_TELEPORTS.REQUESTED_AT,
                        ASKED.plusSeconds(1).toEpochMilli())
                .set(PLAYER_WARP_PENDING_TELEPORTS.PAID_AMOUNT, new BigDecimal("25.5000"))
                .set(PLAYER_WARP_PENDING_TELEPORTS.PAID_CURRENCY, "gold")
                .execute();

        PendingHop found = store.find(ALEX).orElseThrow();

        assertThat(found.kind()).isEqualTo(HopKind.PLAYER_WARP);
        assertThat(found.target()).isEqualTo("7");
        assertThat(store.clear(new PendingHop(
                        ALEX,
                        HopKind.WARP,
                        "market",
                        "survival",
                        "lobby",
                        ASKED.plusSeconds(1),
                        Optional.empty(),
                        Optional.empty())))
                .isZero();
        assertThat(store.clear(found)).isEqualTo(1);
    }

    @Test
    void aKindANewerReleaseAddedReadsAsUnknownAndCanStillBeClaimed() {
        dsl.insertInto(PLAYER_WARP_PENDING_TELEPORTS)
                .set(PLAYER_WARP_PENDING_TELEPORTS.PLAYER_UUID, ALEX.toString())
                .set(PLAYER_WARP_PENDING_TELEPORTS.KIND, "SHIP")
                .set(PLAYER_WARP_PENDING_TELEPORTS.WARP_ID, 0L)
                .set(PLAYER_WARP_PENDING_TELEPORTS.TARGET_REF, "galleon")
                .set(PLAYER_WARP_PENDING_TELEPORTS.TARGET_SERVER, "harbour")
                .set(PLAYER_WARP_PENDING_TELEPORTS.ORIGIN_SERVER, "lobby")
                .set(PLAYER_WARP_PENDING_TELEPORTS.REQUESTED_AT, ASKED.toEpochMilli())
                .execute();

        PendingHop found = store.find(ALEX).orElseThrow();

        assertThat(found.kind()).isEqualTo(HopKind.UNKNOWN);
        assertThat(found.target()).isEqualTo("galleon");
        assertThatThrownBy(() -> store.record(found)).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.clear(found)).isEqualTo(1);
        assertThat(store.find(ALEX)).isEmpty();
    }
}
