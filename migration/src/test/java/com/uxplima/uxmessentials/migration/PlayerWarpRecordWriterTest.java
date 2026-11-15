package com.uxplima.uxmessentials.migration;

import static com.uxplima.uxmessentials.migration.Fixtures.WORLD;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.convert.map.ImportedPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;

/**
 * Every player-warp importer lands its warps through this writer: names that collide across owners, names the new
 * charset rejects, passwords, and a re-run of the same import.
 */
class PlayerWarpRecordWriterTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID STAFF = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final Instant CREATED = Instant.ofEpochMilli(1_700_000_000_000L);

    private final InMemoryPlayerWarps tables = new InMemoryPlayerWarps();
    private final PlayerWarpRecordWriter writer = tables.writer();

    @Test
    void aNameAnotherOwnerHoldsGoesToTheNextFreeSuffixInsteadOfReplacingTheirWarp() {
        PlayerWarp staffs = PlayerWarp.create(
                new PlayerRef(STAFF, "Staff"),
                "Staff",
                PlayerWarpName.of("market"),
                Position.of(WORLD, 0, 64, 0),
                CREATED);
        tables.warps.put(staffs.name(), staffs);

        RecordOutcome alices = writer.write(warp(ALICE, "Market"));
        RecordOutcome bobs = writer.write(warp(BOB, "market"));

        assertThat(alices).isEqualTo(RecordOutcome.WRITTEN);
        assertThat(bobs).isEqualTo(RecordOutcome.WRITTEN);
        assertThat(tables.named("market")).isEqualTo(staffs);
        assertThat(tables.named("market2").owner().uuid()).isEqualTo(ALICE);
        assertThat(tables.named("market3").owner().uuid()).isEqualTo(BOB);
    }

    @Test
    void runningTheSameImportAgainWritesNothingNew() {
        ImportedPlayerWarp shop = withEverything(warp(ALICE, "Shop"));
        writer.write(shop);
        int afterFirstRun = tables.rows();

        RecordOutcome rerun = writer.write(shop);

        assertThat(rerun).isEqualTo(RecordOutcome.SKIPPED);
        assertThat(tables.rows()).isEqualTo(afterFirstRun);
        assertThat(tables.warps).containsOnlyKeys(PlayerWarpName.of("shop"));
    }

    @Test
    void aNameTheNewCharsetRejectsIsFoldedPaddedAndCutUntilItIsOne() {
        writer.write(warp(ALICE, "My Shop!"));
        writer.write(warp(ALICE, "Hi"));
        writer.write(warp(ALICE, "a".repeat(40)));
        writer.write(warp(BOB, "a".repeat(40)));

        assertThat(tables.warps)
                .containsOnlyKeys(
                        PlayerWarpName.of("my-shop-"),
                        PlayerWarpName.of("hi-"),
                        PlayerWarpName.of("a".repeat(32)),
                        PlayerWarpName.of("a".repeat(31) + "2"));
    }

    @Test
    void theOriginalNameSurvivesAsTheDisplayNameAndThePasswordReachesOnlyThePasswordStore() {
        ImportedPlayerWarp shop = withEverything(warp(ALICE, "Shop"));

        writer.write(shop);

        PlayerWarp stored = tables.named("shop");
        assertThat(stored.displayName())
                .hasValueSatisfying(name -> assertThat(name.value()).isEqualTo("Shop"));
        assertThat(stored.access()).isEqualTo(WarpAccess.PASSWORD);
        assertThat(stored.passwordSet()).isTrue();
        assertThat(stored.toString()).doesNotContain(" s3cret ");
        assertThat(tables.passwords).containsValue(" s3cret ");
        assertThat(stored.ratings().count()).isEqualTo(2);
        assertThat(stored.ratings().average()).isEqualTo(4.5);
        assertThat(stored.price().amount()).isEqualByComparingTo("25");
        assertThat(tables.sideRows)
                .containsExactlyInAnyOrder(
                        "member 1 " + BOB + " MANAGER",
                        "whitelist 1 " + BOB,
                        "ban 1 " + STAFF,
                        "favourite 1 " + BOB,
                        "rating 1 " + BOB + " 4",
                        "rating 1 " + STAFF + " 5");
    }

    @Test
    void aDryRunReportsWhatTheImportWouldDoAndWritesNothing() {
        ImportedPlayerWarp shop = withEverything(warp(ALICE, "Shop"));

        RecordOutcome preview = writer.preview(shop);

        assertThat(preview).isEqualTo(RecordOutcome.WRITTEN);
        assertThat(tables.rows()).isZero();
        writer.write(shop);
        assertThat(writer.preview(shop)).isEqualTo(RecordOutcome.SKIPPED);
    }

    private static ImportedPlayerWarp warp(UUID owner, String name) {
        return new ImportedPlayerWarp(
                owner,
                "Owner",
                name,
                Optional.of(name),
                new Position(WORLD, 1, 64, 1, 0f, 0f),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                WarpAccess.PUBLIC,
                Optional.empty(),
                BigDecimal.ZERO,
                "default",
                BigDecimal.ZERO,
                CREATED,
                0L,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static ImportedPlayerWarp withEverything(ImportedPlayerWarp base) {
        return new ImportedPlayerWarp(
                base.owner(),
                base.ownerName(),
                base.name(),
                base.displayName(),
                base.location(),
                Optional.of("shops"),
                Optional.of("Best prices"),
                Optional.of("CHEST"),
                WarpAccess.PASSWORD,
                Optional.of(" s3cret "),
                new BigDecimal("25"),
                "default",
                BigDecimal.ZERO,
                CREATED,
                12L,
                2,
                List.of(
                        new ImportedPlayerWarp.Rating(BOB, 4, CREATED),
                        new ImportedPlayerWarp.Rating(STAFF, 5, CREATED)),
                List.of(new ImportedPlayerWarp.Member(BOB, WarpRole.MANAGER, CREATED)),
                List.of(BOB),
                List.of(new ImportedPlayerWarp.Ban(
                        STAFF, Optional.empty(), Optional.empty(), Optional.empty(), CREATED)),
                List.of(BOB));
    }
}
