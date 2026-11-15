package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps.PLAYER_WARPS;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpQuota;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/**
 * The server a player warp lives on: a backend that records none (off a network, or under the shipped default name)
 * writes none, and the backend holding the warp's world claims it under its real name later.
 */
class PlayerWarpServerTest {

    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");
    private static final WorldRef SURVIVAL_WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000aaaa"), "world");
    private static final WorldRef CREATIVE_WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000bbbb"), "creative");
    private static final PlayerWarpName SHOP = PlayerWarpName.of("shop");
    private static final PlayerWarpName FARM = PlayerWarpName.of("farm");

    @TempDir
    Path folder;

    private DSLContext dsl;
    private JooqPlayerWarpRepository warps;
    private PlayerWarpServerClaimer claimer;

    @BeforeEach
    void open() {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
        dsl = DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), "");
        warps = new JooqPlayerWarpRepository(dsl);
        claimer = new PlayerWarpServerClaimer(dsl);
    }

    private SetPlayerWarp setOn(Optional<String> recordedServer) {
        return new SetPlayerWarp(
                warps,
                new PlayerWarpQuota(new NoNodes(), 10),
                new Notifier((viewer, key, placeholders) -> key.key(), (viewer, text) -> {}),
                event -> {},
                Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC),
                List.of(),
                recordedServer);
    }

    private static Position in(WorldRef world) {
        return Position.of(world, 0, 64, 0);
    }

    private Optional<String> serverOf(PlayerWarpName name) {
        return warps.findByName(name).flatMap(PlayerWarp::serverId);
    }

    @Test
    void aBackendThatRecordsNoServerStampsNoneAndARealNameClaimsItsWarpsLater() {
        setOn(Optional.of("survival")).set(SAM, "Sam", FARM, in(SURVIVAL_WORLD));
        assertThat(serverOf(FARM)).contains("survival");

        SetPlayerWarp standalone = setOn(Optional.empty());
        standalone.set(SAM, "Sam", SHOP, in(SURVIVAL_WORLD));
        standalone.set(SAM, "Sam", FARM, in(CREATIVE_WORLD));

        assertThat(dsl.select(PLAYER_WARPS.SERVER_ID).from(PLAYER_WARPS).fetch(PLAYER_WARPS.SERVER_ID))
                .containsOnlyNulls()
                .hasSize(2);

        assertThat(claimer.claim("survival", List.of(SURVIVAL_WORLD.uid().toString())))
                .isOne();
        assertThat(claimer.claim("creative", List.of(CREATIVE_WORLD.uid().toString())))
                .isOne();
        assertThat(serverOf(SHOP)).contains("survival");
        assertThat(serverOf(FARM)).contains("creative");
    }

    /** No player holds any node, so every quota is the configured default. */
    private static final class NoNodes implements Permissions {

        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }
}
