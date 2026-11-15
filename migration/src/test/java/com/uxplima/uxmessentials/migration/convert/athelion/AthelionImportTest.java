package com.uxplima.uxmessentials.migration.convert.athelion;

import static com.uxplima.uxmessentials.migration.Fixtures.WORLD;
import static com.uxplima.uxmessentials.migration.Fixtures.only;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.Fixtures;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.RecordingLogger;
import com.uxplima.uxmessentials.migration.convert.map.ImportedPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;

/** Athelion's {@code data.yml} read by the source the import runs. */
class AthelionImportTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID THIRD = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID FOURTH = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID BLOCKED = UUID.fromString("55555555-5555-4555-8555-555555555555");

    private final RecordingLogger log = new RecordingLogger();

    private List<ImportedPlayerWarp> importFrom(String resource) {
        Path dataFile = Fixtures.path(resource);
        AthelionPlayerWarpsConvert source = new AthelionPlayerWarpsConvert(Fixtures.ONLY_WORLD, dataFile, log);
        return only(Fixtures.records(source, dataFile), ImportRecord.PlayerWarpRecord.class).stream()
                .map(ImportRecord.PlayerWarpRecord::warp)
                .toList();
    }

    private ImportedPlayerWarp warp(String name) {
        return importFrom("/athelion/data.yml").stream()
                .filter(warp -> warp.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void onlyWarpsWithAnOwnerANameAndAKnownWorldImport() {
        assertThat(importFrom("/athelion/data.yml"))
                .extracting(ImportedPlayerWarp::name)
                .containsExactlyInAnyOrder("Shop", "Vault", "Farm");
    }

    @Test
    void aPasswordWarpKeepsEveryFieldAthelionStoredAndThePasswordExactlyAsTyped() {
        ImportedPlayerWarp shop = warp("Shop");

        assertThat(shop.owner()).isEqualTo(OWNER);
        assertThat(shop.displayName()).contains("&6The Shop");
        assertThat(shop.description()).contains("Best prices in town");
        assertThat(shop.location()).isEqualTo(new Position(WORLD, 10.5, 64, -3.5, 90f, 5f));
        assertThat(shop.access()).isEqualTo(WarpAccess.PASSWORD);
        assertThat(shop.plaintextPassword()).contains(" s3cret ");
        assertThat(shop.price()).isEqualByComparingTo("25");
        assertThat(shop.earnedMoney()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(shop.categoryId()).contains("shops");
        assertThat(shop.visits()).isEqualTo(120);
        assertThat(shop.createdAt()).isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
        assertThat(shop.bans()).extracting(ImportedPlayerWarp.Ban::player).containsExactly(BLOCKED);
    }

    @Test
    void theStarTotalIsSpreadOverTheReviewersSoTheCountAndAverageSurvive() {
        assertThat(warp("Shop").ratings())
                .extracting(ImportedPlayerWarp.Rating::player, ImportedPlayerWarp.Rating::stars)
                .containsExactly(tuple(SECOND, 5), tuple(THIRD, 5), tuple(FOURTH, 4));
        assertThat(warp("Farm").ratings())
                .extracting(ImportedPlayerWarp.Rating::stars)
                .as("a vote never lands outside 1..5")
                .containsExactly(5);
    }

    @Test
    void aClosedWarpIsPrivateAndTheNullPasswordTokenAndTheAllCategoryMeanNone() {
        ImportedPlayerWarp vault = warp("Vault");

        assertThat(vault.access()).isEqualTo(WarpAccess.PRIVATE);
        assertThat(vault.plaintextPassword()).isEmpty();
        assertThat(vault.categoryId()).isEmpty();
        assertThat(vault.displayName()).contains("Vault");
        assertThat(vault.createdAt()).isEqualTo(Instant.EPOCH);
        assertThat(warp("Farm").access()).isEqualTo(WarpAccess.PUBLIC);
        assertThat(warp("Farm").description()).isEqualTo(Optional.empty());
    }

    @Test
    void anUnreadableDataFileImportsNothingAndSaysSo() {
        assertThat(importFrom("/athelion/broken.yml")).isEmpty();
        assertThat(log.warnings()).anySatisfy(line -> assertThat(line).contains("could not be read"));
    }
}
