package com.uxplima.uxmessentials.persistence.homes;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Homes.HOMES;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/** The server a home lives on, from a database the previous release left through the real migrations. */
class HomeServerTest {

    private static final WorldRef SURVIVAL_WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000aaaa"), "world");
    private static final WorldRef CREATIVE_WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000bbbb"), "creative");
    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");
    private static final HomeSlot FIRST = HomeSlot.of(0);

    @TempDir
    Path folder;

    private SQLiteDataSource data;
    private DSLContext dsl;
    private JooqHomeRepository homes;
    private HomeServerClaimer claimer;

    /** A database as the release before homes recorded their server left it. */
    @BeforeEach
    void open() {
        data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        Flyway.configure()
                .dataSource(data)
                .locations("classpath:db/migration")
                .placeholders(Map.of("prefix", ""))
                .target("94")
                .load()
                .migrate();
        dsl = DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), "");
        homes = new JooqHomeRepository(dsl);
        claimer = new HomeServerClaimer(dsl);
    }

    private void upgrade() {
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
    }

    /** A home row written the way a release without the server column writes it. */
    private void oldReleaseSets(PlayerRef owner, WorldRef world) {
        dsl.insertInto(HOMES)
                .set(HOMES.OWNER, owner.uuid().toString())
                .set(HOMES.SLOT, FIRST.index())
                .set(HOMES.WORLD, world.uid().toString())
                .set(HOMES.WORLD_NAME, world.name())
                .set(HOMES.X, 0.0)
                .set(HOMES.Y, 64.0)
                .set(HOMES.Z, 0.0)
                .set(HOMES.YAW, 0.0f)
                .set(HOMES.PITCH, 0.0f)
                .set(HOMES.CREATED_AT, 1L)
                .set(HOMES.UPDATED_AT, 1L)
                .execute();
    }

    private Optional<String> serverOf(PlayerRef owner) {
        return homes.findSlot(owner, FIRST).orElseThrow().knownServer();
    }

    private static Position in(WorldRef world) {
        return new Position(world, 0, 64, 0, 0f, 0f);
    }

    @Test
    void homesFromBeforeTheUpgradeAreUnscopedUntilTheBackendHoldingTheirWorldClaimsThem() {
        oldReleaseSets(ALEX, SURVIVAL_WORLD);
        oldReleaseSets(SAM, CREATIVE_WORLD);
        upgrade();

        assertThat(dsl.select(HOMES.SERVER_ID).from(HOMES).fetch(HOMES.SERVER_ID))
                .containsOnly(HomeServerClaimer.UNSCOPED);
        assertThat(serverOf(ALEX)).isEmpty();

        assertThat(claimer.claim("survival", List.of(SURVIVAL_WORLD.uid().toString())))
                .isOne();

        assertThat(serverOf(ALEX)).contains("survival");
        assertThat(serverOf(SAM)).isEmpty();
        assertThat(claimer.claim("survival", List.of(SURVIVAL_WORLD.uid().toString())))
                .isZero();
        assertThat(claimer.claim("creative", List.of(CREATIVE_WORLD.uid().toString())))
                .isOne();
        assertThat(serverOf(SAM)).contains("creative");
    }

    @Test
    void aBackendWithNoWorldLoadedClaimsNothing() {
        oldReleaseSets(ALEX, SURVIVAL_WORLD);
        upgrade();

        assertThat(claimer.claim("survival", List.of())).isZero();
        assertThat(serverOf(ALEX)).isEmpty();
    }

    @Test
    void aHomeAnOlderBackendOrAnImporterWritesAfterTheUpgradeIsClaimedToo() {
        upgrade();
        oldReleaseSets(ALEX, SURVIVAL_WORLD);
        homes.save(Home.create(SAM, FIRST, in(SURVIVAL_WORLD), Instant.ofEpochMilli(1L)));

        assertThat(claimer.claim("survival", List.of(SURVIVAL_WORLD.uid().toString())))
                .isEqualTo(2);
        assertThat(serverOf(ALEX)).contains("survival");
        assertThat(serverOf(SAM)).contains("survival");
    }

    @Test
    void savingAHomeReadBeforeItWasClaimedKeepsTheClaim() {
        oldReleaseSets(ALEX, SURVIVAL_WORLD);
        upgrade();
        Home readBeforeTheClaim = homes.findSlot(ALEX, FIRST).orElseThrow();
        claimer.claim("survival", List.of(SURVIVAL_WORLD.uid().toString()));

        homes.save(readBeforeTheClaim.withLabel(Optional.of(HomeLabel.of("base")), Instant.ofEpochMilli(2L)));

        assertThat(serverOf(ALEX)).contains("survival");
        assertThat(homes.findSlot(ALEX, FIRST).orElseThrow().label()).contains(HomeLabel.of("base"));
    }

    @Test
    void aHomeSetOrRelocatedRecordsTheServerItWasSetOn() {
        upgrade();

        homes.save(Home.create(ALEX, FIRST, in(SURVIVAL_WORLD), "survival", Instant.ofEpochMilli(1L)));
        assertThat(serverOf(ALEX)).contains("survival");

        Home moved = homes.findSlot(ALEX, FIRST)
                .orElseThrow()
                .relocatedTo(in(CREATIVE_WORLD), Optional.of("creative"), Instant.ofEpochMilli(2L));
        homes.save(moved);

        assertThat(serverOf(ALEX)).contains("creative");
        assertThat(claimer.claim(
                        "survival",
                        List.of(
                                CREATIVE_WORLD.uid().toString(),
                                SURVIVAL_WORLD.uid().toString())))
                .isZero();
    }

    @Test
    void aHomeRelocatedOnABackendThatRecordsNoServerIsLeftForTheClaimNotForItsOldServer() {
        upgrade();
        homes.save(Home.create(ALEX, FIRST, in(SURVIVAL_WORLD), "survival", Instant.ofEpochMilli(1L)));

        homes.save(homes.findSlot(ALEX, FIRST)
                .orElseThrow()
                .relocatedTo(in(CREATIVE_WORLD), Optional.empty(), Instant.ofEpochMilli(2L)));

        assertThat(serverOf(ALEX)).isEmpty();
        assertThat(claimer.claim("creative", List.of(CREATIVE_WORLD.uid().toString())))
                .isOne();
        assertThat(serverOf(ALEX)).contains("creative");
    }

    @Test
    void aRenamedServerTakesEveryHomeRecordedUnderTheOldName() {
        upgrade();
        homes.save(Home.create(ALEX, FIRST, in(SURVIVAL_WORLD), "server-1", Instant.ofEpochMilli(1L)));
        homes.save(Home.create(SAM, FIRST, in(CREATIVE_WORLD), "creative", Instant.ofEpochMilli(1L)));

        int moved = new JooqHomeServers(dsl).rename("server-1", "survival");

        assertThat(moved).isOne();
        assertThat(serverOf(ALEX)).contains("survival");
        assertThat(serverOf(SAM)).contains("creative");
    }
}
