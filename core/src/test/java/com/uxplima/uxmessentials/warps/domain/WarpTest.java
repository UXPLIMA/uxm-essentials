package com.uxplima.uxmessentials.warps.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * The {@link Warp} value object: a default-created warp is free and ungated, a move re-anchors in place
 * while preserving the name/owner/creation-time/gates, a blank required permission collapses to "no extra
 * gate", and {@link Warp#hasCost()} reflects whether the economy gate should run.
 */
class WarpTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Operator");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Instant CREATED = Instant.parse("2026-05-30T12:00:00Z");

    @Test
    void aDefaultWarpIsFreeAndUngated() {
        Warp warp = Warp.create(WarpName.of("shop"), at(1, 64, 1), OWNER, CREATED);

        assertThat(warp.hasCost()).isFalse();
        assertThat(warp.cost().isFree()).isTrue();
        assertThat(warp.requiredPermission()).isEmpty();
    }

    @Test
    void aMovePreservesEverythingButTheLocation() {
        Warp original = Warp.create(
                WarpName.of("vip"),
                at(0, 64, 0),
                OWNER,
                CREATED,
                WarpCost.of(new BigDecimal("50")),
                Optional.of("uxmessentials.warp.vip"));

        Warp moved = original.movedTo(at(100, 70, 100));

        assertThat(moved.name()).isEqualTo(original.name());
        assertThat(moved.owner()).isEqualTo(original.owner());
        assertThat(moved.createdAt()).isEqualTo(CREATED);
        assertThat(moved.cost().amount()).isEqualByComparingTo("50");
        assertThat(moved.requiredPermission()).contains("uxmessentials.warp.vip");
        assertThat(moved.location().blockX()).isEqualTo(100);
    }

    @Test
    void aBlankRequiredPermissionCollapsesToNoGate() {
        Warp warp = Warp.create(WarpName.of("shop"), at(0, 64, 0), OWNER, CREATED, WarpCost.free(), Optional.of("   "));

        assertThat(warp.requiredPermission()).isEmpty();
    }

    @Test
    void hasCostReflectsAPricedWarp() {
        Warp priced = Warp.create(
                WarpName.of("vip"), at(0, 64, 0), OWNER, CREATED, WarpCost.of(BigDecimal.TEN), Optional.empty());

        assertThat(priced.hasCost()).isTrue();
    }

    private static Position at(double x, double y, double z) {
        return Position.of(WORLD, x, y, z);
    }
}
