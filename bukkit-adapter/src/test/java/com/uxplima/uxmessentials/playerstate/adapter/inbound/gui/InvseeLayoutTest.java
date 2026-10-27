package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** The pane that pads the invsee slots with no counterpart on the target is the house filler. */
class InvseeLayoutTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void thePaddingPaneShowsNoTooltipAndHasNoName() {
        ItemStack filler = InvseeLayout.filler();

        assertThat(filler.getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
        assertThat(filler.getItemMeta().isHideTooltip()).isTrue();
        assertThat(filler.getItemMeta().hasDisplayName()).isFalse();
    }
}
