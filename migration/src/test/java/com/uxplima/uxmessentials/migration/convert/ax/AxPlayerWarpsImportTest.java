package com.uxplima.uxmessentials.migration.convert.ax;

import static com.uxplima.uxmessentials.migration.Fixtures.WORLD;
import static com.uxplima.uxmessentials.migration.Fixtures.only;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.FixtureDatabases;
import com.uxplima.uxmessentials.migration.Fixtures;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.RecordingLogger;
import com.uxplima.uxmessentials.migration.convert.map.ImportedPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** An AxPlayerWarps H2 database where AxPlayerWarps keeps it, read by the source the import runs. */
class AxPlayerWarpsImportTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID CAROL = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @TempDir
    Path plugins;

    private final RecordingLogger log = new RecordingLogger();
    private List<ImportedPlayerWarp> warps = List.of();

    @BeforeEach
    void importTheFixture() {
        FixtureDatabases.h2(
                "/axplayerwarps/axplayerwarps.sql",
                plugins.resolve("AxPlayerWarps").resolve("data.mv.db"));
        AxPlayerWarpsConvert source = new AxPlayerWarpsConvert(
                new AxPlayerWarpsConfig(Optional.empty(), "", "", Optional.of(plugins)), Fixtures.ONLY_WORLD, log);
        warps = only(Fixtures.records(source, plugins), ImportRecord.PlayerWarpRecord.class).stream()
                .map(ImportRecord.PlayerWarpRecord::warp)
                .toList();
    }

    private ImportedPlayerWarp warp(String name) {
        return warps.stream()
                .filter(warp -> warp.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aWarpInAnUnknownWorldOrWithoutItsOwnerIsLeftOutAndTheOwnerlessOneIsReported() {
        assertThat(warps).extracting(ImportedPlayerWarp::name).containsExactlyInAnyOrder("Market", "Secret", "Hideout");
        assertThat(log.warnings()).anySatisfy(line -> assertThat(line).contains("Orphan", "99"));
    }

    @Test
    void theWarpRowAndItsLookupTablesLandOnOneWarp() {
        ImportedPlayerWarp market = warp("Market");

        assertThat(market.owner()).isEqualTo(ALICE);
        assertThat(market.ownerName()).isEqualTo("Alice");
        assertThat(market.displayName()).contains("Market");
        assertThat(market.location()).isEqualTo(new Position(WORLD, 10.5, 64, -3.5, 90f, 5f));
        assertThat(market.description()).contains("Cheap tools");
        assertThat(market.categoryId()).contains("shops");
        assertThat(market.icon()).contains("CHEST");
        assertThat(market.access()).isEqualTo(WarpAccess.PUBLIC);
        assertThat(market.plaintextPassword()).isEmpty();
        assertThat(market.price()).isEqualByComparingTo("12.5");
        assertThat(market.earnedMoney()).isEqualByComparingTo("40");
        assertThat(market.createdAt()).isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
    }

    @Test
    void sideListsResolveTheirPlayerIdsAndARowForAnUnknownPlayerIsLeftOut() {
        ImportedPlayerWarp market = warp("Market");

        assertThat(market.ratings())
                .extracting(ImportedPlayerWarp.Rating::player, ImportedPlayerWarp.Rating::stars)
                .as("a vote never lands outside 1..5")
                .containsExactlyInAnyOrder(tuple(BOB, 4), tuple(CAROL, 5));
        assertThat(market.bans())
                .containsExactly(new ImportedPlayerWarp.Ban(
                        BOB,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Instant.ofEpochMilli(1_700_000_500_000L)));
        assertThat(market.favourites()).containsExactly(CAROL);
        assertThat(market.visits()).isEqualTo(3);
        assertThat(market.uniqueVisitors()).isEqualTo(2);
        assertThat(warp("Secret").whitelist()).containsExactly(CAROL);
    }

    @Test
    void theAccessOrdinalMapsToPublicWhitelistAndPrivate() {
        assertThat(warp("Market").access()).isEqualTo(WarpAccess.PUBLIC);
        assertThat(warp("Secret").access()).isEqualTo(WarpAccess.WHITELIST);
        assertThat(warp("Hideout").access()).isEqualTo(WarpAccess.PRIVATE);
    }
}
