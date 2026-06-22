package com.uxplima.uxmessentials.npc.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The NPC type-selector icon mapping: each mob shows its own spawn egg, the fake player shows a player head, and
 * a type with no spawn egg (the display entities) falls back to a plain egg rather than breaking the selector.
 * MockBukkit is mocked only to make the {@code Material} registry resolvable for {@code Material.getMaterial}.
 */
class NpcTypeIconTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aMobShowsItsOwnSpawnEgg() {
        assertThat(NpcTypeIcon.iconFor(EntityType.ZOMBIE)).isEqualTo(Material.ZOMBIE_SPAWN_EGG);
        assertThat(NpcTypeIcon.iconFor(EntityType.VILLAGER)).isEqualTo(Material.VILLAGER_SPAWN_EGG);
        assertThat(NpcTypeIcon.iconFor(EntityType.ALLAY)).isEqualTo(Material.ALLAY_SPAWN_EGG);
    }

    @Test
    void theFakePlayerShowsAPlayerHead() {
        // The player is the default NPC type and has no spawn egg, so it falls back to the player head.
        assertThat(NpcTypeIcon.iconFor(EntityType.PLAYER)).isEqualTo(Material.PLAYER_HEAD);
    }

    @Test
    void aTypeWithoutASpawnEggFallsBackToAPlainEgg() {
        // The display entities are renderable NPC types but have no <NAME>_SPAWN_EGG material.
        assertThat(NpcTypeIcon.iconFor(EntityType.TEXT_DISPLAY)).isEqualTo(Material.EGG);
        assertThat(NpcTypeIcon.iconFor(EntityType.BLOCK_DISPLAY)).isEqualTo(Material.EGG);
        assertThat(NpcTypeIcon.iconFor(EntityType.ITEM_DISPLAY)).isEqualTo(Material.EGG);
    }
}
