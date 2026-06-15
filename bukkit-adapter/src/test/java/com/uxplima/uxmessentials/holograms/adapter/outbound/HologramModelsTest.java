package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Pins the renderer's content resolution and its fail-soft contract: an item hologram's {@code Material} name
 * resolves to an item stack and a block hologram's BlockData string parses to live block data, while an unknown
 * material or an unparseable BlockData string yields {@code null} (the renderer logs and skips it) rather than
 * throwing. This is the seam the renderer dispatches through per {@link com.uxplima.uxmessentials.holograms.domain.HologramType}.
 */
class HologramModelsTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void resolvesAKnownMaterialNameToAnItemStack() {
        assertThat(HologramModels.itemOf("DIAMOND")).isNotNull();
        assertThat(HologramModels.itemOf("diamond")).isNotNull(); // case-insensitive
    }

    @Test
    void anUnknownOrBlankMaterialResolvesToNullRatherThanThrowing() {
        assertThat(HologramModels.itemOf("NOT_A_REAL_ITEM")).isNull();
        assertThat(HologramModels.itemOf("  ")).isNull();
        assertThat(HologramModels.itemOf(null)).isNull();
    }

    @Test
    void resolvedItemIsTheNamedMaterial() {
        assertThat(java.util.Objects.requireNonNull(HologramModels.itemOf("EMERALD"))
                        .getType())
                .isEqualTo(Material.EMERALD);
    }

    @Test
    void parsesAValidBlockDataString() {
        assertThat(HologramModels.blockOf("minecraft:oak_log[axis=y]")).isNotNull();
        assertThat(HologramModels.blockOf("stone")).isNotNull();
    }

    @Test
    void anInvalidOrBlankBlockDataStringResolvesToNullRatherThanThrowing() {
        assertThat(HologramModels.blockOf("not a block")).isNull();
        assertThat(HologramModels.blockOf("minecraft:oak_log[axis=banana]")).isNull();
        assertThat(HologramModels.blockOf(" ")).isNull();
        assertThat(HologramModels.blockOf(null)).isNull();
    }
}
