package com.uxplima.uxmessentials.homes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/** Visibility behaviour on the {@link Home} value object: new homes are private and {@code withVisibility} flips and re-stamps. */
class HomeTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Owner");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position LOCATION = Position.of(WORLD, 1, 64, 1);

    @Test
    void aNewHomeIsPrivate() {
        Home home = Home.create(OWNER, HomeSlot.of(0), LOCATION, Instant.EPOCH);

        assertThat(home.isPublic()).isFalse();
    }

    @Test
    void withVisibilityPublicFlipsTheFlagAndBumpsUpdatedAt() {
        Home home = Home.create(OWNER, HomeSlot.of(0), LOCATION, Instant.EPOCH);
        Instant later = Instant.EPOCH.plusSeconds(60);

        Home shared = home.withVisibility(true, later);

        assertThat(shared.isPublic()).isTrue();
        assertThat(shared.updatedAt()).isEqualTo(later);
        // createdAt is preserved, slot and location are unchanged.
        assertThat(shared.createdAt()).isEqualTo(Instant.EPOCH);
        assertThat(shared.slot()).isEqualTo(HomeSlot.of(0));
        assertThat(shared.location()).isEqualTo(LOCATION);
    }

    @Test
    void withVisibilityCarriesThroughLabelAndIcon() {
        Home home = Home.create(OWNER, HomeSlot.of(0), LOCATION, Instant.EPOCH)
                .withLabel(java.util.Optional.of(HomeLabel.of("Base")), Instant.EPOCH)
                .withIcon(java.util.Optional.of(HomeIcon.of("DIAMOND_BLOCK")), Instant.EPOCH);

        Home shared = home.withVisibility(true, Instant.EPOCH.plusSeconds(1));

        assertThat(shared.label()).contains(HomeLabel.of("Base"));
        assertThat(shared.icon()).contains(HomeIcon.of("DIAMOND_BLOCK"));
        assertThat(shared.isPublic()).isTrue();
    }
}
