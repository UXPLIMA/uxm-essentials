package com.uxplima.uxmessentials.persistence.moderation;

import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationJailLocations.MODERATION_JAIL_LOCATIONS;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/** The server a jail location lives on, from a database the previous release left through the real migrations. */
class JailServerTest {

    private static final WorldRef PRISON_WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000aaaa"), "prison");
    private static final WorldRef LOBBY_WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000bbbb"), "lobby");

    @TempDir
    Path folder;

    private SQLiteDataSource data;
    private DSLContext dsl;
    private JooqJailLocationStore jails;
    private JailServerClaimer claimer;

    /** A database as the release before jail locations recorded their server left it. */
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
        jails = new JooqJailLocationStore(dsl);
        claimer = new JailServerClaimer(dsl);
    }

    private void upgrade() {
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
    }

    /** A jail row written the way a release without the server column writes it. */
    private void oldReleaseSets(String name, WorldRef world) {
        dsl.insertInto(MODERATION_JAIL_LOCATIONS)
                .set(MODERATION_JAIL_LOCATIONS.NAME, name)
                .set(MODERATION_JAIL_LOCATIONS.WORLD, world.uid().toString())
                .set(MODERATION_JAIL_LOCATIONS.WORLD_NAME, world.name())
                .set(MODERATION_JAIL_LOCATIONS.X, 0.0)
                .set(MODERATION_JAIL_LOCATIONS.Y, 64.0)
                .set(MODERATION_JAIL_LOCATIONS.Z, 0.0)
                .set(MODERATION_JAIL_LOCATIONS.YAW, 0.0f)
                .set(MODERATION_JAIL_LOCATIONS.PITCH, 0.0f)
                .set(MODERATION_JAIL_LOCATIONS.CREATED_AT, 1L)
                .execute();
    }

    private Optional<String> serverOf(String name) {
        return jails.find(name).orElseThrow().knownServer();
    }

    private static Position in(WorldRef world, double x) {
        return new Position(world, x, 64, 0, 0f, 0f);
    }

    @Test
    void jailsFromBeforeTheUpgradeAreUnscopedUntilTheBackendHoldingTheirWorldClaimsThem() {
        oldReleaseSets("cell", PRISON_WORLD);
        oldReleaseSets("pit", LOBBY_WORLD);
        upgrade();

        assertThat(dsl.select(MODERATION_JAIL_LOCATIONS.SERVER_ID)
                        .from(MODERATION_JAIL_LOCATIONS)
                        .fetch(MODERATION_JAIL_LOCATIONS.SERVER_ID))
                .containsOnly(StoredJail.UNSCOPED);
        assertThat(serverOf("cell")).isEmpty();

        assertThat(claimer.claim("prison", List.of(PRISON_WORLD.uid().toString())))
                .containsExactly("cell");
        assertThat(serverOf("cell")).contains("prison");
        assertThat(serverOf("pit")).isEmpty();
        assertThat(claimer.claim("prison", List.of(PRISON_WORLD.uid().toString())))
                .isEmpty();
        assertThat(claimer.claim("lobby", List.of(LOBBY_WORLD.uid().toString())))
                .containsExactly("pit");
        assertThat(serverOf("pit")).contains("lobby");
    }

    @Test
    void aBackendWithNoWorldLoadedClaimsNothing() {
        oldReleaseSets("cell", PRISON_WORLD);
        upgrade();

        assertThat(claimer.claim("prison", List.of())).isEmpty();
        assertThat(serverOf("cell")).isEmpty();
    }

    @Test
    void aJailAnOlderBackendCreatesDuringTheUpgradeIsClaimedToo() {
        upgrade();
        oldReleaseSets("cell", PRISON_WORLD);

        assertThat(claimer.claim("prison", List.of(PRISON_WORLD.uid().toString())))
                .containsExactly("cell");
    }

    @Test
    void setJailRecordsTheServerAndASaveWithoutOneKeepsIt() {
        upgrade();

        jails.save(StoredJail.of("cell", in(PRISON_WORLD, 1), "prison"));
        assertThat(serverOf("cell")).contains("prison");

        jails.save(StoredJail.of("cell", in(PRISON_WORLD, 2)));
        StoredJail kept = jails.find("cell").orElseThrow();
        assertThat(kept.knownServer()).contains("prison");
        assertThat(kept.location().x()).isEqualTo(2.0);

        jails.save(StoredJail.of("cell", in(LOBBY_WORLD, 3), "lobby"));
        assertThat(serverOf("cell")).contains("lobby");
    }
}
