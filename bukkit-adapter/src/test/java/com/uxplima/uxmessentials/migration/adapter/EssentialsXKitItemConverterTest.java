package com.uxplima.uxmessentials.migration.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class EssentialsXKitItemConverterTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void resolvesAMaterialNameAndDefaultsAmountToOne() {
        ItemStack stack = EssentialsXKitItemConverter.toStack("diamond_sword").orElseThrow();

        assertThat(stack.getType()).isEqualTo(Material.DIAMOND_SWORD);
        assertThat(stack.getAmount()).isEqualTo(1);
    }

    @Test
    void parsesTheAmountFromTheSecondToken() {
        ItemStack stack = EssentialsXKitItemConverter.toStack("cooked_beef 16").orElseThrow();

        assertThat(stack.getType()).isEqualTo(Material.COOKED_BEEF);
        assertThat(stack.getAmount()).isEqualTo(16);
    }

    @Test
    void appliesAModernEnchantmentToken() {
        ItemStack stack = EssentialsXKitItemConverter.toStack("diamond_sword 1 sharpness:5")
                .orElseThrow();

        assertThat(stack.getEnchantmentLevel(Enchantment.SHARPNESS)).isEqualTo(5);
    }

    @Test
    void mapsLegacyBukkitEnchantNames() {
        ItemStack stack = EssentialsXKitItemConverter.toStack("diamond_pickaxe 1 dig_speed:3 durability:2")
                .orElseThrow();

        assertThat(stack.getEnchantmentLevel(Enchantment.EFFICIENCY)).isEqualTo(3);
        assertThat(stack.getEnchantmentLevel(Enchantment.UNBREAKING)).isEqualTo(2);
    }

    @Test
    void anUnknownMaterialYieldsEmpty() {
        assertThat(EssentialsXKitItemConverter.toStack("definitely_not_an_item 1"))
                .isEmpty();
    }

    @Test
    void aBlankDescriptorYieldsEmpty() {
        assertThat(EssentialsXKitItemConverter.toStack("   ")).isEmpty();
    }
}
