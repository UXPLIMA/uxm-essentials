package com.uxplima.uxmessentials.npc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class EquipmentSlotTest {

    @Test
    void parsesEachSlotCaseInsensitively() {
        assertThat(EquipmentSlot.parse("mainhand")).contains(EquipmentSlot.MAINHAND);
        assertThat(EquipmentSlot.parse("OffHand")).contains(EquipmentSlot.OFFHAND);
        assertThat(EquipmentSlot.parse("  HEAD ")).contains(EquipmentSlot.HEAD);
        assertThat(EquipmentSlot.parse("feet")).contains(EquipmentSlot.FEET);
    }

    @Test
    void rejectsAnUnknownOrBlankWord() {
        assertThat(EquipmentSlot.parse("belt")).isEmpty();
        assertThat(EquipmentSlot.parse("")).isEmpty();
        assertThat(EquipmentSlot.parse("   ")).isEmpty();
        assertThat(EquipmentSlot.parse(null)).isEqualTo(Optional.empty());
    }
}
