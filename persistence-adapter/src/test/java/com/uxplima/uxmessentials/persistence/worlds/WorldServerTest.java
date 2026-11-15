package com.uxplima.uxmessentials.persistence.worlds;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.worlds.domain.GeneratorRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/** The server a managed world belongs to, from a database the previous release left, through the real migrations. */
class WorldServerTest {

    private static final String LOBBY = "lobby";
    private static final String SURVIVAL = "survival";
    private static final String LOBBY_WORLD_UID = "00000000-0000-0000-0000-00000000aaaa";
    private static final String SURVIVAL_WORLD_UID = "00000000-0000-0000-0000-00000000bbbb";
    private static final String CREATOR = "00000000-0000-0000-0000-000000000005";
    private static final WorldName WORLD = WorldName.of("world");
    private static final WorldName ARENA = WorldName.of("arena");

    @TempDir
    Path folder;

    private SQLiteDataSource data;
    private DSLContext dsl;

    /** A database as the release before worlds recorded their server left it. */
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
    }

    private void upgrade() {
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
    }

    /**
     * A world row, every nullable column filled, and one setting, written the way a release without the server column
     * writes them.
     */
    private void oldReleaseRegisters(WorldName name, Optional<String> uid, String pvp) {
        dsl.insertInto(DSL.table("world"))
                .columns(
                        DSL.field("name"),
                        DSL.field("uid"),
                        DSL.field("environment"),
                        DSL.field("world_type"),
                        DSL.field("seed"),
                        DSL.field("generator_ref"),
                        DSL.field("dimension"),
                        DSL.field("generate_structures"),
                        DSL.field("alias"),
                        DSL.field("auto_load"),
                        DSL.field("adopted"),
                        DSL.field("created_at"),
                        DSL.field("created_by"))
                .values(
                        name.value(),
                        uid.orElse(null),
                        "NORMAL",
                        "NORMAL",
                        42L,
                        "uxmEssentials:void",
                        "mypack:mining",
                        1,
                        "The " + name.value(),
                        1,
                        0,
                        1L,
                        CREATOR)
                .execute();
        dsl.insertInto(DSL.table("world_setting"))
                .columns(DSL.field("world_name"), DSL.field("setting_key"), DSL.field("setting_value"))
                .values(name.value(), "pvp", pvp)
                .execute();
    }

    private JooqWorldRepository on(String server) {
        return new JooqWorldRepository(dsl, server);
    }

    private WorldServerClaimer claimer() {
        return new WorldServerClaimer(dsl);
    }

    private static ManagedWorld fresh(WorldName name) {
        return ManagedWorld.created(name, WorldSpec.normal(), true, Optional.empty(), Instant.ofEpochMilli(1L));
    }

    @Test
    void theUpgradeKeepsEveryWorldColumnAndItsSettingsAsUnclaimed() {
        oldReleaseRegisters(WORLD, Optional.of(LOBBY_WORLD_UID), "false");

        upgrade();

        List<NetworkWorld> network = on(LOBBY).network();
        assertThat(network).hasSize(1);
        assertThat(network.get(0).unclaimed()).isTrue();
        ManagedWorld world = network.get(0).world();
        assertThat(world.knownUid()).contains(UUID.fromString(LOBBY_WORLD_UID));
        assertThat(world.spec().seed()).contains(42L);
        assertThat(world.spec().generator()).contains(GeneratorRef.of("uxmEssentials:void"));
        assertThat(world.spec().dimension()).isPresent();
        assertThat(world.alias()).contains("The world");
        assertThat(world.createdBy()).contains(UUID.fromString(CREATOR));
        assertThat(world.settings().get(WorldProperties.PVP)).isFalse();
        assertThat(on(LOBBY).all()).isEmpty();
    }

    @Test
    void aServerThatRecordsNoServerKeepsReadingAndWritingTheUnscopedRows() {
        oldReleaseRegisters(WORLD, Optional.of(LOBBY_WORLD_UID), "false");
        upgrade();
        JooqWorldRepository standalone = on(NetworkWorld.UNSCOPED);

        standalone.save(fresh(ARENA));

        assertThat(standalone.find(WORLD).orElseThrow().settings().get(WorldProperties.PVP))
                .isFalse();
        assertThat(standalone.network()).extracting(NetworkWorld::server).containsOnly(NetworkWorld.UNSCOPED);
        assertThat(standalone.all()).hasSize(2);
    }

    @Test
    void aServerNamedLaterClaimsTheWorldsItKeptWhileItRecordedNoServer() {
        upgrade();
        ManagedWorld kept = fresh(ARENA);
        on(NetworkWorld.UNSCOPED).save(kept.withSettings(kept.settings().with(WorldProperties.PLAYER_LIMIT, 4)));

        WorldServerClaimer.Claims claims = claimer().claimPresent(LOBBY, Map.of(), Set.of("arena"));

        assertThat(claims.claimed()).containsExactly("arena");
        assertThat(on(LOBBY).find(ARENA).orElseThrow().settings().get(WorldProperties.PLAYER_LIMIT))
                .isEqualTo(4);
        assertThat(on(NetworkWorld.UNSCOPED).all()).isEmpty();
    }

    @Test
    void twoServersWithAWorldOfTheSameNameKeepSeparateRowsAndSettings() {
        upgrade();
        ManagedWorld lobbys = fresh(WORLD);
        on(LOBBY).save(lobbys.withSettings(lobbys.settings().with(WorldProperties.PVP, false)));
        ManagedWorld survivals = fresh(WORLD);
        on(SURVIVAL).save(survivals.withSettings(survivals.settings().with(WorldProperties.PLAYER_LIMIT, 7)));

        ManagedWorld lobbyRead = on(LOBBY).find(WORLD).orElseThrow();
        ManagedWorld survivalRead = on(SURVIVAL).find(WORLD).orElseThrow();

        assertThat(lobbyRead.settings().get(WorldProperties.PVP)).isFalse();
        assertThat(lobbyRead.settings().get(WorldProperties.PLAYER_LIMIT)).isZero();
        assertThat(survivalRead.settings().get(WorldProperties.PVP)).isTrue();
        assertThat(survivalRead.settings().get(WorldProperties.PLAYER_LIMIT)).isEqualTo(7);
        assertThat(on(LOBBY).network()).extracting(NetworkWorld::server).containsExactly(LOBBY, SURVIVAL);
    }

    @Test
    void deletingAWorldOnOneServerLeavesTheSameNamedWorldOfAnotherAlone() {
        upgrade();
        on(LOBBY).save(fresh(WORLD));
        on(SURVIVAL).save(fresh(WORLD));

        on(LOBBY).delete(WORLD);

        assertThat(on(LOBBY).exists(WORLD)).isFalse();
        assertThat(on(SURVIVAL).exists(WORLD)).isTrue();
    }

    @Test
    void aServerClaimsTheWorldItHasLoadedUnderTheRecordedUidWithItsSettings() {
        oldReleaseRegisters(WORLD, Optional.of(LOBBY_WORLD_UID), "false");
        upgrade();

        WorldServerClaimer.Claims claims =
                claimer().claimPresent(LOBBY, Map.of("world", LOBBY_WORLD_UID), Set.of("world"));

        assertThat(claims.claimed()).containsExactly("world");
        assertThat(claims.copied()).isEmpty();
        assertThat(on(LOBBY).find(WORLD).orElseThrow().settings().get(WorldProperties.PVP))
                .isFalse();
    }

    @Test
    void aServerWithASameNamedWorldOfAnotherUidGetsACopyAndLeavesTheSharedRowForItsOwner() {
        oldReleaseRegisters(WORLD, Optional.of(LOBBY_WORLD_UID), "false");
        upgrade();

        WorldServerClaimer.Claims survivals =
                claimer().claimPresent(SURVIVAL, Map.of("world", SURVIVAL_WORLD_UID), Set.of("world"));
        WorldServerClaimer.Claims lobbys =
                claimer().claimPresent(LOBBY, Map.of("world", LOBBY_WORLD_UID), Set.of("world"));

        assertThat(survivals.claimed()).isEmpty();
        assertThat(survivals.copied()).containsExactly("world");
        assertThat(lobbys.claimed()).containsExactly("world");
        ManagedWorld survivalCopy = on(SURVIVAL).find(WORLD).orElseThrow();
        assertThat(survivalCopy.knownUid()).contains(UUID.fromString(SURVIVAL_WORLD_UID));
        assertThat(survivalCopy.settings().get(WorldProperties.PVP)).isFalse();
        assertThat(on(LOBBY).find(WORLD).orElseThrow().knownUid()).contains(UUID.fromString(LOBBY_WORLD_UID));
        assertThat(on(LOBBY).network()).noneMatch(NetworkWorld::unclaimed);
    }

    @Test
    void aWorldFolderClaimsARowWhenNoWorldOfThatNameIsLoaded() {
        oldReleaseRegisters(ARENA, Optional.of(SURVIVAL_WORLD_UID), "true");
        oldReleaseRegisters(WorldName.of("games"), Optional.empty(), "true");
        upgrade();

        WorldServerClaimer.Claims lobbys =
                claimer().claimPresent(LOBBY, Map.of("world", LOBBY_WORLD_UID), Set.of("world"));
        WorldServerClaimer.Claims survivals =
                claimer().claimPresent(SURVIVAL, Map.of("world", SURVIVAL_WORLD_UID), Set.of("world", "arena"));

        assertThat(lobbys.isEmpty()).isTrue();
        assertThat(survivals.claimed()).containsExactly("arena");
        assertThat(on(LOBBY).network())
                .filteredOn(NetworkWorld::unclaimed)
                .extracting(world -> world.name().value())
                .containsExactly("games");
    }

    @Test
    void aClaimedRowIsNeverTakenAgainByTheNextServer() {
        oldReleaseRegisters(ARENA, Optional.empty(), "true");
        upgrade();

        claimer().claimPresent(LOBBY, Map.of(), Set.of("arena"));
        WorldServerClaimer.Claims second = claimer().claimPresent(SURVIVAL, Map.of(), Set.of("arena"));

        assertThat(second.isEmpty()).isTrue();
        assertThat(on(LOBBY).exists(ARENA)).isTrue();
        assertThat(on(SURVIVAL).exists(ARENA)).isFalse();
    }

    @Test
    void aServerThatAlreadyKeepsAWorldOfThatNameLeavesTheUnclaimedRowAlone() {
        oldReleaseRegisters(WORLD, Optional.empty(), "false");
        upgrade();
        on(LOBBY).save(fresh(WORLD));

        WorldServerClaimer.Claims claims = claimer().claimPresent(LOBBY, Map.of(), Set.of("world"));

        assertThat(claims.isEmpty()).isTrue();
        assertThat(on(LOBBY).find(WORLD).orElseThrow().settings().get(WorldProperties.PVP))
                .isTrue();
        assertThat(on(LOBBY).network()).filteredOn(NetworkWorld::unclaimed).hasSize(1);
    }

    @Test
    void anOperatorClaimsAnUnclaimedWorldByNameOnlyOnce() {
        oldReleaseRegisters(ARENA, Optional.empty(), "false");
        upgrade();

        boolean first = claimer().claimNamed(SURVIVAL, "arena");
        boolean second = claimer().claimNamed(LOBBY, "arena");

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(on(SURVIVAL).find(ARENA).orElseThrow().settings().get(WorldProperties.PVP))
                .isFalse();
    }

    @Test
    void aRenameMovesEveryWorldOfOneServerWithItsSettingsAndSkipsANameTheTargetRuns() {
        upgrade();
        ManagedWorld arena = fresh(ARENA);
        on("server-1").save(arena.withSettings(arena.settings().with(WorldProperties.PLAYER_LIMIT, 3)));
        on("server-1").save(fresh(WORLD));
        on(LOBBY).save(fresh(WORLD));

        int moved = claimer().rename("server-1", LOBBY);

        assertThat(moved).isOne();
        assertThat(on(LOBBY).find(ARENA).orElseThrow().settings().get(WorldProperties.PLAYER_LIMIT))
                .isEqualTo(3);
        assertThat(on("server-1").all())
                .extracting(world -> world.name().value())
                .containsExactly("world");
    }

    @Test
    void theCacheServesThisServersWorldsAndTheWholeNetworkFromOneSnapshot() {
        upgrade();
        on(SURVIVAL).save(fresh(ARENA));
        CachedWorldRepository lobby = new CachedWorldRepository(on(LOBBY), LOBBY);

        lobby.save(fresh(WORLD));

        assertThat(lobby.all()).extracting(world -> world.name().value()).containsExactly("world");
        assertThat(lobby.find(ARENA)).isEmpty();
        assertThat(lobby.network()).hasSize(2);
    }
}
