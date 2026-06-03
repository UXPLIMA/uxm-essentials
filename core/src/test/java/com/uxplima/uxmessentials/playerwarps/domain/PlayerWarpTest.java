package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

class PlayerWarpTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Owner");

    @Test
    void newWarpIsPrivateByDefault() {
        PlayerWarp warp = PlayerWarp.create(OWNER, PlayerWarpName.of("base"), at(0, 64, 0), Instant.EPOCH);

        assertThat(warp.isPublic()).isFalse();
        assertThat(warp.owner()).isEqualTo(OWNER);
    }

    @Test
    void movedToReanchorsKeepingNameOwnerVisibilityAndCreation() {
        PlayerWarp original = PlayerWarp.create(OWNER, PlayerWarpName.of("base"), at(0, 64, 0), Instant.EPOCH)
                .withVisibility(true);

        PlayerWarp moved = original.movedTo(at(100, 70, 100));

        assertThat(moved.location().blockX()).isEqualTo(100);
        assertThat(moved.isPublic()).isTrue();
        assertThat(moved.name()).isEqualTo(original.name());
        assertThat(moved.createdAt()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void withVisibilityFlipsThePublicFlag() {
        PlayerWarp warp = PlayerWarp.create(OWNER, PlayerWarpName.of("base"), at(0, 64, 0), Instant.EPOCH);

        assertThat(warp.withVisibility(true).isPublic()).isTrue();
        assertThat(warp.withVisibility(true).withVisibility(false).isPublic()).isFalse();
    }

    private static Position at(double x, double y, double z) {
        return Position.of(WORLD, x, y, z);
    }
}
