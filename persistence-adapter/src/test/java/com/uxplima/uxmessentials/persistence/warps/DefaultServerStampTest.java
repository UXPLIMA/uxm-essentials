package com.uxplima.uxmessentials.persistence.warps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Warps.WARPS;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.playerwarps.JooqPlayerWarpRepository;
import com.uxplima.uxmessentials.persistence.playerwarps.PlayerWarpServerClaimer;
import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/**
 * Warps and player warps a standalone backend stamped with the shipped default server name, from a database the
 * previous release left through the real migrations: the upgrade clears that name so the backend holding each world
 * claims it under its real name, and leaves every other server alone.
 */
class DefaultServerStampTest {

    private static final WorldRef SURVIVAL_WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000aaaa"), "world");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");
    private static final Instant CREATED = Instant.ofEpochMilli(1L);

    @TempDir
    Path folder;

    @Test
    void theDefaultNameIsClearedForTheClaimAndARealNameIsKept() {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        Flyway.configure()
                .dataSource(data)
                .locations("classpath:db/migration")
                .placeholders(Map.of("prefix", ""))
                .target("97")
                .load()
                .migrate();
        DSLContext dsl = DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), "");
        JooqWarpRepository warps = new JooqWarpRepository(dsl);
        JooqPlayerWarpRepository playerWarps = new JooqPlayerWarpRepository(dsl);
        Position spawn = new Position(SURVIVAL_WORLD, 0, 64, 0, 0f, 0f);
        warps.save(Warp.create(WarpName.of("market"), spawn, SAM, CREATED).onServer("server-1"));
        warps.save(Warp.create(WarpName.of("plots"), spawn, SAM, CREATED).onServer("survival"));
        playerWarps.save(PlayerWarp.create(SAM, "Sam", PlayerWarpName.of("shop"), spawn, CREATED)
                .onServer(Optional.of("server-1")));
        playerWarps.save(PlayerWarp.create(SAM, "Sam", PlayerWarpName.of("farm"), spawn, CREATED)
                .onServer(Optional.of("survival")));

        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());

        assertThat(dsl.select(WARPS.SERVER_ID)
                        .from(WARPS)
                        .where(WARPS.NAME.eq("market"))
                        .fetchOne(WARPS.SERVER_ID))
                .isEqualTo(Warp.UNSCOPED);
        assertThat(warps.find(WarpName.of("plots")).orElseThrow().server()).contains("survival");
        assertThat(playerWarps
                        .findByName(PlayerWarpName.of("shop"))
                        .orElseThrow()
                        .serverId())
                .isEmpty();
        assertThat(playerWarps
                        .findByName(PlayerWarpName.of("farm"))
                        .orElseThrow()
                        .serverId())
                .contains("survival");

        List<String> local = List.of(SURVIVAL_WORLD.uid().toString());
        assertThat(new WarpServerClaimer(dsl).claim("lobby", local)).containsExactly("market");
        assertThat(new PlayerWarpServerClaimer(dsl).claim("lobby", local)).isOne();
        assertThat(playerWarps
                        .findByName(PlayerWarpName.of("shop"))
                        .orElseThrow()
                        .serverId())
                .contains("lobby");
        assertThat(playerWarps
                        .findByName(PlayerWarpName.of("farm"))
                        .orElseThrow()
                        .serverId())
                .contains("survival");
    }
}
