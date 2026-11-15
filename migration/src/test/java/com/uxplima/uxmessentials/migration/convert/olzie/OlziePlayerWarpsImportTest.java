package com.uxplima.uxmessentials.migration.convert.olzie;

import static com.uxplima.uxmessentials.migration.Fixtures.WORLD;
import static com.uxplima.uxmessentials.migration.Fixtures.only;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.FixtureDatabases;
import com.uxplima.uxmessentials.migration.Fixtures;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.RecordingLogger;
import com.uxplima.uxmessentials.migration.convert.jdbc.JdbcConnection;
import com.uxplima.uxmessentials.migration.convert.map.ImportedPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** An Olzie PlayerWarps SQLite file where Olzie keeps it, read read-only by the source the import runs. */
class OlziePlayerWarpsImportTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID THIRD = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID FOURTH = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @TempDir
    Path plugins;

    private final RecordingLogger log = new RecordingLogger();
    private Path database = Path.of("unset");
    private List<ImportedPlayerWarp> warps = List.of();

    @BeforeEach
    void importTheFixture() {
        database = FixtureDatabases.sqlite(
                "/olzie/playerwarps.sql",
                plugins.resolve("PlayerWarps").resolve("data").resolve("database.db"));
        warps = runImport();
    }

    private List<ImportedPlayerWarp> runImport() {
        OlziePlayerWarpsConvert source = new OlziePlayerWarpsConvert(config(), Fixtures.ONLY_WORLD, log);
        return only(Fixtures.records(source, plugins), ImportRecord.PlayerWarpRecord.class).stream()
                .map(ImportRecord.PlayerWarpRecord::warp)
                .toList();
    }

    private OlziePlayerWarpsConfig config() {
        return new OlziePlayerWarpsConfig(Optional.empty(), "", "", Optional.of(plugins));
    }

    private ImportedPlayerWarp warp(String name) {
        return warps.stream()
                .filter(warp -> warp.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aWarpInAnUnknownWorldOrWithoutAnOwnerAccountIsLeftOut() {
        assertThat(warps)
                .extracting(ImportedPlayerWarp::name)
                .containsExactlyInAnyOrder("Arena", "Garden", "Den", "Open");
    }

    @Test
    void aPasswordWarpKeepsItsFieldsAndThePasswordExactlyAsStored() {
        ImportedPlayerWarp arena = warp("Arena");

        assertThat(arena.owner()).isEqualTo(OWNER);
        assertThat(arena.ownerName()).isEmpty();
        assertThat(arena.location()).isEqualTo(new Position(WORLD, 10.5, 64, -3.5, 90f, 5f));
        assertThat(arena.description()).contains("Fight club");
        assertThat(arena.categoryId()).contains("pvp");
        assertThat(arena.icon()).contains("DIAMOND_SWORD");
        assertThat(arena.access()).isEqualTo(WarpAccess.PASSWORD);
        assertThat(arena.plaintextPassword()).contains("pw ");
        assertThat(arena.price()).isEqualByComparingTo("5.5");
        assertThat(arena.visits()).isEqualTo(50);
        assertThat(arena.createdAt()).isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
    }

    @Test
    void sideTablesFollowTheWarpRowidAndAMalformedPlayerIsLeftOut() {
        ImportedPlayerWarp arena = warp("Arena");

        assertThat(arena.ratings())
                .extracting(ImportedPlayerWarp.Rating::player, ImportedPlayerWarp.Rating::stars)
                .as("a vote never lands outside 1..5")
                .containsExactlyInAnyOrder(tuple(SECOND, 4), tuple(THIRD, 5));
        assertThat(arena.members())
                .containsExactly(new ImportedPlayerWarp.Member(SECOND, WarpRole.MANAGER, Instant.EPOCH));
        assertThat(arena.bans())
                .containsExactlyInAnyOrder(
                        new ImportedPlayerWarp.Ban(
                                THIRD,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Instant.ofEpochMilli(1_700_000_500_000L)),
                        new ImportedPlayerWarp.Ban(
                                FOURTH, Optional.empty(), Optional.of("griefing"), Optional.empty(), Instant.EPOCH));
        assertThat(arena.favourites()).containsExactly(THIRD);
        assertThat(arena.uniqueVisitors()).isEqualTo(2);
        assertThat(warp("Garden").whitelist()).containsExactly(THIRD);
    }

    @Test
    void aPasswordWinsOverAWhitelistWhichWinsOverALock() {
        assertThat(warp("Garden").access()).isEqualTo(WarpAccess.WHITELIST);
        assertThat(warp("Den").access()).isEqualTo(WarpAccess.PRIVATE);
        assertThat(warp("Den").plaintextPassword()).isEmpty();
        assertThat(warp("Den").categoryId()).isEmpty();
        assertThat(warp("Open").access()).isEqualTo(WarpAccess.PUBLIC);
        assertThat(warp("Open").plaintextPassword()).isEmpty();
        assertThat(warp("Open").visits()).isZero();
    }

    @Test
    void theConnectionTheImportOpensRefusesEveryWriteSoTheOperatorsFileIsLeftAsItWas() throws Exception {
        byte[] before = Files.readAllBytes(database);
        JdbcConnection connection = config().connection().orElseThrow();

        List<ImportedPlayerWarp> again = runImport();

        try (Connection opened = DriverManager.getConnection(connection.url());
                Statement statement = opened.createStatement()) {
            assertThatThrownBy(() -> statement.execute("DELETE FROM playerwarps_warps"))
                    .isInstanceOf(SQLException.class);
        }
        assertThat(again).hasSameSizeAs(warps);
        assertThat(Files.readAllBytes(database)).isEqualTo(before);
        assertThat(log.warnings()).isEmpty();
    }
}
