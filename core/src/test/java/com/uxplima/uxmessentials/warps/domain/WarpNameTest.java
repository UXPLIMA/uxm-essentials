package com.uxplima.uxmessentials.warps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * {@link WarpName} normalisation and the invariants the column width and the per-warp permission node
 * depend on: a name is trimmed and lower-cased so lookup and uniqueness are case-insensitive, blank and
 * overlong input is rejected at construction, and the {@code uxmessentials.warp.use.<warp>} node is derived
 * from the canonical form.
 */
class WarpNameTest {

    @Test
    void normalisesToTrimmedLowercase() {
        assertThat(WarpName.of("  Shop  ").value()).isEqualTo("shop");
        assertThat(WarpName.of("PvP").value()).isEqualTo("pvp");
    }

    @Test
    void twoCasingsAddressTheSameWarp() {
        assertThat(WarpName.of("Spawn")).isEqualTo(WarpName.of("spawn"));
    }

    @Test
    void rejectsBlankInput() {
        assertThatThrownBy(() -> WarpName.of("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOverlongInput() {
        String tooLong = "a".repeat(WarpName.MAX_LENGTH + 1);

        assertThatThrownBy(() -> WarpName.of(tooLong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void derivesThePerWarpPermissionNodeFromTheCanonicalForm() {
        assertThat(WarpName.of("Shop").useNode()).isEqualTo("uxmessentials.warp.use.shop");
    }
}
