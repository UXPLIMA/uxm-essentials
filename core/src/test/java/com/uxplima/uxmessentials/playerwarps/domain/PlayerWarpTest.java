package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

class PlayerWarpTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Owner");

    @Test
    void createYieldsAnUnsavedPrivateActiveWarp() {
        PlayerWarp warp = PlayerWarp.create(OWNER, "Owner", PlayerWarpName.of("base"), at(0, 64, 0), Instant.EPOCH);

        assertThat(warp.id()).isEmpty();
        assertThat(warp.owner()).isEqualTo(OWNER);
        assertThat(warp.ownerName()).isEqualTo("Owner");
        assertThat(warp.access()).isEqualTo(WarpAccess.PRIVATE);
        assertThat(warp.status()).isEqualTo(WarpStatus.ACTIVE);
        assertThat(warp.passwordSet()).isFalse();
        assertThat(warp.price().isFree()).isTrue();
        assertThat(warp.earnings().isZero()).isTrue();
        assertThat(warp.ratings()).isEqualTo(RatingSummary.empty());
        assertThat(warp.visits()).isEqualTo(VisitSummary.empty());
        assertThat(warp.favouriteCount()).isZero();
        assertThat(warp.displayName()).isEmpty();
        assertThat(warp.categoryId()).isEmpty();
        assertThat(warp.sponsorship()).isEmpty();
        assertThat(warp.rent()).isEmpty();
        assertThat(warp.createdAt()).isEqualTo(Instant.EPOCH);
        assertThat(warp.updatedAt()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void movedToReanchorsKeepingIdOwnerNameAndBumpsUpdatedAt() {
        PlayerWarp original = PlayerWarp.create(OWNER, "Owner", PlayerWarpName.of("base"), at(0, 64, 0), Instant.EPOCH)
                .withId(PlayerWarpId.of(7))
                .withAccess(WarpAccess.PUBLIC, Instant.EPOCH);
        Instant later = Instant.EPOCH.plusSeconds(60);

        PlayerWarp moved = original.movedTo(at(100, 70, 100), later);

        assertThat(moved.location().blockX()).isEqualTo(100);
        assertThat(moved.id()).contains(PlayerWarpId.of(7));
        assertThat(moved.name()).isEqualTo(original.name());
        assertThat(moved.access()).isEqualTo(WarpAccess.PUBLIC);
        assertThat(moved.createdAt()).isEqualTo(Instant.EPOCH);
        assertThat(moved.updatedAt()).isEqualTo(later);
    }

    @Test
    void withAccessChangesTheAccessAxisAndBumpsUpdatedAt() {
        PlayerWarp warp = PlayerWarp.create(OWNER, "Owner", PlayerWarpName.of("base"), at(0, 64, 0), Instant.EPOCH);
        Instant later = Instant.EPOCH.plusSeconds(30);

        PlayerWarp shared = warp.withAccess(WarpAccess.PUBLIC, later);

        assertThat(shared.access()).isEqualTo(WarpAccess.PUBLIC);
        assertThat(shared.updatedAt()).isEqualTo(later);
        assertThat(shared.withAccess(WarpAccess.PRIVATE, later).access()).isEqualTo(WarpAccess.PRIVATE);
    }

    @Test
    void withIconCarriesTheIconAndBumpsUpdatedAt() {
        PlayerWarp warp = PlayerWarp.create(OWNER, "Owner", PlayerWarpName.of("base"), at(0, 64, 0), Instant.EPOCH);
        Instant later = Instant.EPOCH.plusSeconds(15);

        PlayerWarp iconed = warp.withIcon(Optional.of(IconSpec.of("DIAMOND")), later);

        assertThat(iconed.icon()).contains(IconSpec.of("DIAMOND"));
        assertThat(iconed.updatedAt()).isEqualTo(later);
    }

    @Test
    void withDescriptionCarriesTheDescriptionAndBumpsUpdatedAt() {
        PlayerWarp warp = PlayerWarp.create(OWNER, "Owner", PlayerWarpName.of("base"), at(0, 64, 0), Instant.EPOCH);
        Instant later = Instant.EPOCH.plusSeconds(20);

        PlayerWarp described = warp.withDescription(Optional.of(WarpDescription.of("come visit")), later);

        assertThat(described.description()).contains(WarpDescription.of("come visit"));
        assertThat(described.updatedAt()).isEqualTo(later);
    }

    @Test
    void withEffectsCarriesTheEffectsAndBumpsUpdatedAt() {
        PlayerWarp warp = PlayerWarp.create(OWNER, "Owner", PlayerWarpName.of("base"), at(0, 64, 0), Instant.EPOCH);
        Instant later = Instant.EPOCH.plusSeconds(25);
        WarpEffects effects = new WarpEffects(
                Optional.of("ENTITY_ENDERMAN_TELEPORT"), Optional.empty(), Optional.empty(), Optional.empty());

        PlayerWarp effected = warp.withEffects(effects, later);

        assertThat(effected.effects()).isEqualTo(effects);
        assertThat(effected.updatedAt()).isEqualTo(later);
    }

    @Test
    void withTimingCarriesTheOverridesAndBumpsUpdatedAt() {
        PlayerWarp warp = PlayerWarp.create(OWNER, "Owner", PlayerWarpName.of("base"), at(0, 64, 0), Instant.EPOCH);
        Instant later = Instant.EPOCH.plusSeconds(30);
        WarpTimingOverrides timing = new WarpTimingOverrides(Optional.of(3.0), Optional.of(5.0));

        PlayerWarp timed = warp.withTiming(timing, later);

        assertThat(timed.timing()).isEqualTo(timing);
        assertThat(timed.updatedAt()).isEqualTo(later);
    }

    @Test
    void withCategoryIdCarriesTheCategoryAndBumpsUpdatedAt() {
        PlayerWarp warp = PlayerWarp.create(OWNER, "Owner", PlayerWarpName.of("base"), at(0, 64, 0), Instant.EPOCH);
        Instant later = Instant.EPOCH.plusSeconds(35);

        PlayerWarp filed = warp.withCategoryId(Optional.of("shops"), later);

        assertThat(filed.categoryId()).contains("shops");
        assertThat(filed.updatedAt()).isEqualTo(later);
    }

    @Test
    void withIdAssignsTheSurrogateWithoutBumpingUpdatedAt() {
        PlayerWarp warp = PlayerWarp.create(OWNER, "Owner", PlayerWarpName.of("base"), at(0, 64, 0), Instant.EPOCH);

        PlayerWarp saved = warp.withId(PlayerWarpId.of(42));

        assertThat(saved.id()).contains(PlayerWarpId.of(42));
        assertThat(saved.updatedAt()).isEqualTo(Instant.EPOCH);
    }

    private static Position at(double x, double y, double z) {
        return Position.of(WORLD, x, y, z);
    }
}
