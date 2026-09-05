package com.uxplima.uxmessentials.shared.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Sound;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The name forms an operator may write in a config where a sound is asked for. Every module that plays a
 * configured sound goes through this one resolver, so the accepted spellings are settled here rather than
 * per module.
 */
class BukkitRegistryKeysTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void resolvesADotNotationKey() {
        assertThat(BukkitRegistryKeys.resolveSound("entity.enderman.teleport")).isNotNull();
    }

    @Test
    void resolvesTheConstantFormOfAKeyWhoseSegmentsHaveNoUnderscores() {
        assertThat(BukkitRegistryKeys.resolveSound("ENTITY_ENDERMAN_TELEPORT")).isNotNull();
    }

    @Test
    void resolvesTheConstantFormOfAKeyWithAnUnderscoreInsideASegment() {
        // block.note_block.pling is BLOCK_NOTE_BLOCK_PLING as a constant, and the constant spelling cannot say
        // which of its underscores are dots. Turning every underscore into a dot yields block.note.block.pling,
        // which names no sound, so the whole effect went silent with nothing logged.
        Sound resolved = BukkitRegistryKeys.resolveSound("BLOCK_NOTE_BLOCK_PLING");
        assertThat(resolved).isNotNull();
        assertThat(resolved).isEqualTo(BukkitRegistryKeys.resolveSound("block.note_block.pling"));
    }

    @Test
    void anUnknownNameIsNull() {
        assertThat(BukkitRegistryKeys.resolveSound("definitely_not_a_real_sound_xyz"))
                .isNull();
    }

    @Test
    void aBlankNameIsNull() {
        assertThat(BukkitRegistryKeys.resolveSound("   ")).isNull();
    }
}
