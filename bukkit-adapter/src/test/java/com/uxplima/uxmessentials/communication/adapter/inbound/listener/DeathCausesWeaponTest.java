package com.uxplima.uxmessentials.communication.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The {@code {killer_weapon}} value {@link DeathCauses#weaponName(ItemStack)} produces from a real item stack, over
 * MockBukkit's item factory: a renamed item reads back its custom display name as plain text (any tooltip italic
 * dropped), a plain item reads back a readable material name, and an empty hand reads back the empty string so the
 * token never renders raw.
 */
class DeathCausesWeaponTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aRenamedItemReadsBackItsCustomName() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.displayName(Component.text("Excalibur").decoration(TextDecoration.ITALIC, true));
        sword.setItemMeta(meta);

        assertThat(DeathCauses.weaponName(sword)).isEqualTo("Excalibur");
    }

    @Test
    void aPlainItemReadsBackItsReadableMaterialName() {
        assertThat(DeathCauses.weaponName(new ItemStack(Material.DIAMOND_SWORD)))
                .isEqualTo("Diamond Sword");
    }

    @Test
    void anEmptyHandReadsBackTheEmptyString() {
        assertThat(DeathCauses.weaponName(new ItemStack(Material.AIR))).isEmpty();
    }
}
