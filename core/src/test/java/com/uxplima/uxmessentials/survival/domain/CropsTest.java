package com.uxplima.uxmessentials.survival.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pins the farm-assist crop → seed mapping: the mapped crops resolve to their planting item, the unmapped ones don't. */
class CropsTest {

    @Test
    void mapsEachSupportedCropToItsPlantingItem() {
        assertThat(Crops.seedFor("WHEAT")).contains("WHEAT_SEEDS");
        assertThat(Crops.seedFor("CARROTS")).contains("CARROT");
        assertThat(Crops.seedFor("POTATOES")).contains("POTATO");
        assertThat(Crops.seedFor("BEETROOTS")).contains("BEETROOT_SEEDS");
        assertThat(Crops.seedFor("NETHER_WART")).contains("NETHER_WART");
    }

    @Test
    void hasNoSeedForACropWithoutAPlantableItem() {
        assertThat(Crops.seedFor("PUMPKIN_STEM")).isEmpty();
        assertThat(Crops.seedFor("COCOA")).isEmpty();
    }
}
