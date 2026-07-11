package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The reserved-name set: the {@code /pwarp} verb tokens a warp name may not collide with, so a warp named after a
 * subcommand cannot become unreachable through {@code /pwarp <name>}. The command-side drift guard pins that every
 * registered literal is present here; these tests pin the reservation itself and its case-insensitivity.
 */
class ReservedWarpNamesTest {

    @Test
    void verbTokensAreReserved() {
        assertThat(ReservedWarpNames.isReserved(PlayerWarpName.of("set"))).isTrue();
        assertThat(ReservedWarpNames.isReserved(PlayerWarpName.of("admin"))).isTrue();
        assertThat(ReservedWarpNames.isReserved(PlayerWarpName.of("members"))).isTrue();
        assertThat(ReservedWarpNames.isReserved(PlayerWarpName.of("whitelist"))).isTrue();
        assertThat(ReservedWarpNames.isReserved(PlayerWarpName.of("rate"))).isTrue();
    }

    @Test
    void ordinaryNamesAreNotReserved() {
        assertThat(ReservedWarpNames.isReserved(PlayerWarpName.of("base"))).isFalse();
        assertThat(ReservedWarpNames.isReserved(PlayerWarpName.of("my-shop_2"))).isFalse();
    }

    @Test
    void reservationIsCaseInsensitiveThroughTheCanonicalName() {
        // PlayerWarpName.of lower-cases input, so a mixed-case verb still resolves to the reserved token.
        assertThat(ReservedWarpNames.isReserved(PlayerWarpName.of("Admin"))).isTrue();
        assertThat(ReservedWarpNames.isReserved(PlayerWarpName.of("SET"))).isTrue();
    }

    @Test
    void theTokenSetIsUnmodifiable() {
        assertThatThrownBy(() -> ReservedWarpNames.tokens().add("hax"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
