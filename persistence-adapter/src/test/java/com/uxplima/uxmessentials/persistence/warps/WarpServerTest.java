package com.uxplima.uxmessentials.persistence.warps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Warps.WARPS;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.application.MoveWarp;
import com.uxplima.uxmessentials.warps.application.SetWarp;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/** The server a warp lives on, from a database the previous release left through the real migrations. */
class WarpServerTest {

    private static final WorldRef SURVIVAL_WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000aaaa"), "world");
    private static final WorldRef CREATIVE_WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000bbbb"), "creative");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");
    private static final WarpName MARKET = WarpName.of("market");
    private static final WarpName PLOTS = WarpName.of("plots");

    @TempDir
    Path folder;

    private SQLiteDataSource data;
    private DSLContext dsl;
    private JooqWarpRepository warps;
    private WarpServerClaimer claimer;

    /** A database as the release before warps recorded their server left it. */
    @BeforeEach
    void open() {
        data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        Flyway.configure()
                .dataSource(data)
                .locations("classpath:db/migration")
                .placeholders(Map.of("prefix", ""))
                .target("91")
                .load()
                .migrate();
        dsl = DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), "");
        warps = new JooqWarpRepository(dsl);
        claimer = new WarpServerClaimer(dsl);
    }

    private void upgrade() {
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
    }

    /** A warp row written the way a release without the server column writes it. */
    private void oldReleaseSets(WarpName name, WorldRef world) {
        dsl.insertInto(WARPS)
                .set(WARPS.NAME, name.value())
                .set(WARPS.WORLD, world.uid().toString())
                .set(WARPS.WORLD_NAME, world.name())
                .set(WARPS.X, 0.0)
                .set(WARPS.Y, 64.0)
                .set(WARPS.Z, 0.0)
                .set(WARPS.YAW, 0.0f)
                .set(WARPS.PITCH, 0.0f)
                .set(WARPS.OWNER, SAM.uuid().toString())
                .set(WARPS.CREATED_AT, 1L)
                .execute();
    }

    private Optional<String> serverOf(WarpName name) {
        return warps.find(name).orElseThrow().server();
    }

    private static Warp warpIn(WarpName name, WorldRef world) {
        return Warp.create(name, new Position(world, 0, 64, 0, 0f, 0f), SAM, Instant.ofEpochMilli(1L));
    }

    @Test
    void warpsFromBeforeTheUpgradeAreUnscopedUntilTheBackendHoldingTheirWorldClaimsThem() {
        oldReleaseSets(MARKET, SURVIVAL_WORLD);
        oldReleaseSets(PLOTS, CREATIVE_WORLD);
        upgrade();

        assertThat(dsl.select(WARPS.SERVER_ID).from(WARPS).fetch(WARPS.SERVER_ID))
                .containsOnly(WarpServerClaimer.UNSCOPED);
        assertThat(serverOf(MARKET)).isEmpty();

        List<String> claimed =
                claimer.claim("survival", List.of(SURVIVAL_WORLD.uid().toString()));

        assertThat(claimed).containsExactly("market");
        assertThat(serverOf(MARKET)).contains("survival");
        assertThat(serverOf(PLOTS)).isEmpty();
        assertThat(claimer.claim("survival", List.of(SURVIVAL_WORLD.uid().toString())))
                .isEmpty();
        assertThat(claimer.claim("creative", List.of(CREATIVE_WORLD.uid().toString())))
                .containsExactly("plots");
        assertThat(serverOf(PLOTS)).contains("creative");
    }

    @Test
    void aBackendWithNoWorldLoadedClaimsNothing() {
        oldReleaseSets(MARKET, SURVIVAL_WORLD);
        upgrade();

        assertThat(claimer.claim("survival", List.of())).isEmpty();
        assertThat(serverOf(MARKET)).isEmpty();
    }

    @Test
    void aWarpAnOlderBackendCreatesDuringTheRollingUpgradeIsClaimedToo() {
        upgrade();
        oldReleaseSets(MARKET, SURVIVAL_WORLD);

        assertThat(claimer.claim("survival", List.of(SURVIVAL_WORLD.uid().toString())))
                .containsExactly("market");
        assertThat(serverOf(MARKET)).contains("survival");
    }

    @Test
    void savingAWarpReadBeforeItWasClaimedKeepsTheClaim() {
        oldReleaseSets(MARKET, SURVIVAL_WORLD);
        upgrade();
        Warp readBeforeTheClaim = warps.find(MARKET).orElseThrow();
        claimer.claim("survival", List.of(SURVIVAL_WORLD.uid().toString()));

        warps.save(readBeforeTheClaim.incrementedVisitors());

        assertThat(serverOf(MARKET)).contains("survival");
        assertThat(warps.find(MARKET).orElseThrow().visitors()).isOne();
    }

    @Test
    void aWarpSetOrMovedRecordsTheServerItWasSetOn() {
        upgrade();

        warps.save(warpIn(MARKET, SURVIVAL_WORLD).onServer("survival"));
        assertThat(serverOf(MARKET)).contains("survival");

        Warp moved = warps.find(MARKET)
                .orElseThrow()
                .movedTo(new Position(CREATIVE_WORLD, 1, 70, 1, 0f, 0f), Optional.of("creative"));
        warps.save(moved);

        assertThat(serverOf(MARKET)).contains("creative");
        assertThat(claimer.claim(
                        "survival",
                        List.of(
                                CREATIVE_WORLD.uid().toString(),
                                SURVIVAL_WORLD.uid().toString())))
                .isEmpty();
    }

    @Test
    void aBackendThatRecordsNoServerStampsNoneAndARealNameClaimsItsWarpsLater() {
        upgrade();
        Notifier quiet = new Notifier((viewer, key, placeholders) -> key.key(), (viewer, text) -> {});
        SetWarp setWarp = new SetWarp(
                warps,
                quiet,
                event -> {},
                Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC),
                List.of(),
                Optional.empty());
        MoveWarp moveWarp = new MoveWarp(warps, quiet, Optional.empty());

        setWarp.set(SAM, MARKET, new Position(SURVIVAL_WORLD, 0, 64, 0, 0f, 0f));
        warps.save(warpIn(PLOTS, SURVIVAL_WORLD).onServer("survival"));
        moveWarp.move(SAM, PLOTS, new Position(CREATIVE_WORLD, 1, 70, 1, 0f, 0f));

        assertThat(dsl.select(WARPS.NAME, WARPS.SERVER_ID)
                        .from(WARPS)
                        .orderBy(WARPS.NAME)
                        .fetch()
                        .map(row -> row.value1() + "=" + row.value2()))
                .containsExactly("market=null", "plots=" + WarpServerClaimer.UNSCOPED);

        assertThat(claimer.claim("survival", List.of(SURVIVAL_WORLD.uid().toString())))
                .containsExactly("market");
        assertThat(claimer.claim("creative", List.of(CREATIVE_WORLD.uid().toString())))
                .containsExactly("plots");
        assertThat(serverOf(MARKET)).contains("survival");
        assertThat(serverOf(PLOTS)).contains("creative");
    }
}
