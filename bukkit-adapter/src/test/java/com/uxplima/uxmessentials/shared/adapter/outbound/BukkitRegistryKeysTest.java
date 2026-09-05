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

    @Test
    void theKeyNameOfAConstantIsTheRegistrySpelling() {
        assertThat(BukkitRegistryKeys.soundKeyName("BLOCK_NOTE_BLOCK_PLING")).isEqualTo("block.note_block.pling");
        assertThat(BukkitRegistryKeys.soundKeyName("ENTITY_ENDERMAN_TELEPORT")).isEqualTo("entity.enderman.teleport");
    }

    @Test
    void theKeyNameOfADottedNameIsItself() {
        assertThat(BukkitRegistryKeys.soundKeyName("block.note_block.pling")).isEqualTo("block.note_block.pling");
    }

    @Test
    void theKeyNameOfAResourcePackKeyPassesThrough() {
        // A key the vanilla registry does not know is the operator's own, from a resource pack. Rejecting it would
        // silence a sound that works today, so the name goes to the client exactly as written apart from its case.
        assertThat(BukkitRegistryKeys.soundKeyName("myserver:custom.ding")).isEqualTo("myserver:custom.ding");
        assertThat(BukkitRegistryKeys.soundKeyName("not_a_sound_at_all")).isEqualTo("not_a_sound_at_all");
    }
}
