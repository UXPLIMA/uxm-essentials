package com.uxplima.uxmessentials.migration.convert.essentialsx;

import static com.uxplima.uxmessentials.migration.EssentialsXFixture.ALEX;
import static com.uxplima.uxmessentials.migration.EssentialsXFixture.STEVE;
import static com.uxplima.uxmessentials.migration.Fixtures.WORLD;
import static com.uxplima.uxmessentials.migration.Fixtures.only;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.migration.Fixtures;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.map.ImportedModeration;
import com.uxplima.uxmessentials.migration.convert.map.ImportedUser;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A {@code plugins/Essentials} tree read through the source the import runs: three players, four warps and a kits
 * file, with the malformed and half-usable entries a real server collects over the years.
 */
class EssentialsXImportTest {

    private static final UUID NOTCH = UUID.fromString("3c9d2e41-7a6b-4c5d-8e9f-0a1b2c3d4e5f");
    private static final Issuer ESSENTIALSX = Issuer.console("EssentialsX");

    private final EssentialsXConvert source = new EssentialsXConvert(Fixtures.ONLY_WORLD);
    private final List<ImportRecord> records = Fixtures.records(source, Fixtures.path("/essentialsx"));

    @Test
    void everyReadablePlayerFileBecomesOneUserAndTheUnreadableOnesAreLeftOut() {
        assertThat(users()).extracting(user -> user.owner().uuid()).containsExactlyInAnyOrder(STEVE, ALEX, NOTCH);
    }

    @Test
    void aHomeInAWorldTheServerDoesNotKnowIsDroppedAndTheRestKeepTheirPlaceAndLabel() {
        assertThat(user(ALEX).homes()).singleElement().satisfies(home -> {
            assertThat(home.label()).contains(HomeLabel.of("farm"));
            assertThat(home.location()).isEqualTo(new Position(WORLD, 100, 70, 100, 0f, 0f));
            assertThat(home.slot().index()).isZero();
        });
    }

    @Test
    void aBalanceIsCarriedExactlyAndOneThatIsNotANumberIsLeftOutWithoutLosingThePlayer() {
        assertThat(user(STEVE).balance()).contains(new BigDecimal("1250.5"));
        assertThat(user(NOTCH).balance()).contains(new BigDecimal("-25.00"));
        assertThat(user(ALEX).balance()).isEmpty();
        assertThat(user(ALEX).homes()).isNotEmpty();
    }

    @Test
    void aPlayerWithNoLastAccountNameIsNamedByTheirNickname() {
        assertThat(user(NOTCH).owner()).isEqualTo(new PlayerRef(NOTCH, "Notch"));
    }

    @Test
    void aMutedAndJailedPlayerCarriesBothSanctionsWithTheTimersEssentialsXRecorded() {
        ImportedModeration alex = moderation(ALEX);

        assertThat(alex.hasMute()).isTrue();
        assertThat(alex.hasJail()).isTrue();
        assertThat(alex.profile().mute())
                .isEqualTo(MuteState.timed(
                        Instant.ofEpochMilli(1_900_000_000_000L), ESSENTIALSX, Optional.of("spamming"), Instant.EPOCH));
        assertThat(alex.profile().jail())
                .isEqualTo(JailState.onlineTimed(
                        "cells", Duration.ofMinutes(10), ESSENTIALSX, Optional.empty(), Instant.EPOCH));
    }

    @Test
    void anOldMuteTimeoutStillReadsAndAJailWithNoJailNameIsLeftOut() {
        ImportedModeration notch = moderation(NOTCH);

        assertThat(notch.hasMute()).isTrue();
        assertThat(notch.hasJail()).isFalse();
        assertThat(notch.profile().mute())
                .isEqualTo(MuteState.timed(
                        Instant.ofEpochMilli(1_950_000_000_000L), ESSENTIALSX, Optional.empty(), Instant.EPOCH));
        assertThat(notch.profile().jail()).isEqualTo(JailState.none());
    }

    @Test
    void aPlayerWithoutSanctionsBringsNoModerationRecord() {
        assertThat(only(records, ImportRecord.ModerationRecord.class))
                .extracting(rec -> rec.moderation().profile().target().uuid())
                .containsExactlyInAnyOrder(ALEX, NOTCH);
    }

    @Test
    void warpsInAKnownWorldImportUnderEitherWorldKeyAndTheOthersAreLeftOut() {
        assertThat(only(records, ImportRecord.WarpRecord.class))
                .extracting(rec -> rec.warp().warp())
                .extracting(Warp::name, Warp::location)
                .containsExactlyInAnyOrder(
                        tuple(WarpName.of("spawn"), new Position(WORLD, 0.5, 65, 0.5, 180f, 10f)),
                        tuple(WarpName.of("market"), new Position(WORLD, 40, 64, -12, 0f, 0f)));
    }

    @Test
    void aKitKeepsItsCooldownAndEachItemLineWithItsAmount() {
        assertThat(kit("starter").cooldown()).isEqualTo(Duration.ofSeconds(60));
        assertThat(kit("starter").items())
                .containsExactly(
                        KitItem.of("stone_sword 1", 1),
                        KitItem.of("bread 16", 16),
                        KitItem.of("diamond_pickaxe 1 efficiency:5", 1),
                        KitItem.of("torch", 1));
        assertThat(kit("tools").cooldown()).isZero();
        assertThat(kit("tools").items()).containsExactly(KitItem.of("iron_axe 0", 1));
    }

    @Test
    void aNegativeDelayIsAKitEachPlayerClaimsOnceAndDoesNotCostTheOtherKits() {
        assertThat(kit("welcome").oneTime()).isTrue();
        assertThat(kit("welcome").cooldown()).isZero();
        assertThat(kit("starter").oneTime()).isFalse();
    }

    @Test
    void aKitThatCannotBeImportedIsLeftOutWithoutTakingTheOtherKitsOrTheRestOfTheImportWithIt() {
        assertThat(only(records, ImportRecord.KitRecord.class))
                .extracting(rec -> rec.kit().definition().id())
                .containsExactlyInAnyOrder(KitId.of("starter"), KitId.of("welcome"), KitId.of("tools"));
        assertThat(users()).hasSize(3);
    }

    @Test
    void theSourceRecognisesAnEssentialsFolderAndNothingElse(@TempDir Path empty) {
        assertThat(source.detect(Fixtures.path("/essentialsx"))).isTrue();
        assertThat(source.detect(empty)).isFalse();
        assertThat(Fixtures.records(source, empty)).isEmpty();
    }

    private List<ImportedUser> users() {
        return only(records, ImportRecord.UserRecord.class).stream()
                .map(ImportRecord.UserRecord::user)
                .toList();
    }

    private ImportedUser user(UUID uuid) {
        return users().stream()
                .filter(user -> user.owner().uuid().equals(uuid))
                .findFirst()
                .orElseThrow();
    }

    private ImportedModeration moderation(UUID uuid) {
        return only(records, ImportRecord.ModerationRecord.class).stream()
                .map(ImportRecord.ModerationRecord::moderation)
                .filter(mod -> mod.profile().target().uuid().equals(uuid))
                .findFirst()
                .orElseThrow();
    }

    private KitDefinition kit(String id) {
        return only(records, ImportRecord.KitRecord.class).stream()
                .map(rec -> rec.kit().definition())
                .filter(kit -> kit.id().equals(KitId.of(id)))
                .findFirst()
                .orElseThrow();
    }
}
