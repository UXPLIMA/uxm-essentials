package com.uxplima.uxmessentials.vanish.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link VanishContainers} classifies which container {@code InventoryType} names broadcast an open animation and sound
 * to onlookers, so a vanished opener's access to those must be silenced. The lidded blocks (chest, ender chest, shulker
 * box, barrel) broadcast; the silent workstations (furnace, hopper, anvil, ...) do not.
 */
class VanishContainersTest {

    @Test
    void theLiddedContainersBroadcastTheirOpen() {
        assertThat(VanishContainers.broadcastsOpen("CHEST")).isTrue();
        assertThat(VanishContainers.broadcastsOpen("ENDER_CHEST")).isTrue();
        assertThat(VanishContainers.broadcastsOpen("SHULKER_BOX")).isTrue();
        assertThat(VanishContainers.broadcastsOpen("BARREL")).isTrue();
    }

    @Test
    void silentContainersDoNotBroadcast() {
        assertThat(VanishContainers.broadcastsOpen("FURNACE")).isFalse();
        assertThat(VanishContainers.broadcastsOpen("HOPPER")).isFalse();
        assertThat(VanishContainers.broadcastsOpen("ANVIL")).isFalse();
        assertThat(VanishContainers.broadcastsOpen("CRAFTING")).isFalse();
        assertThat(VanishContainers.broadcastsOpen("UNKNOWN_TYPE")).isFalse();
    }
}
