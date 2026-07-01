package com.uxplima.uxmessentials.shared.menu.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuItemMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Unit proof for the anti-dupe mark: {@link MenuItemMark#mark} writes the persistent-data byte the sweep reads and
 * {@link MenuItemMark#isMarked} reads it back, while a fresh item, a null, and an AIR stack all read unmarked. This
 * pins the one key the renderer and the {@code MenuAntiDupeListener} share, so a display copy that escapes into a real
 * inventory is distinguishable from a genuine player item. MockBukkit backs the {@code ItemMeta} persistent-data
 * container the mark lives on.
 */
class MenuItemMarkTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void markWritesTheKeyAndIsMarkedReadsItBack() {
        ItemStack item = new ItemStack(Material.DIAMOND);

        ItemStack marked = MenuItemMark.mark(item);

        assertThat(MenuItemMark.isMarked(marked))
                .as("a marked item reads back as marked")
                .isTrue();
    }

    @Test
    void markLeavesTheKeyAsAByteOnTheMeta() {
        ItemStack marked = MenuItemMark.mark(new ItemStack(Material.PAPER));

        Byte flag = marked.getItemMeta().getPersistentDataContainer().get(MenuItemMark.KEY, PersistentDataType.BYTE);
        assertThat(flag)
                .as("the mark rides on the item meta as the shared BYTE key")
                .isNotNull();
    }

    @Test
    void aFreshItemIsNotMarked() {
        assertThat(MenuItemMark.isMarked(new ItemStack(Material.DIAMOND)))
                .as("an item the engine never rendered carries no mark")
                .isFalse();
    }

    @Test
    void nullIsNotMarked() {
        assertThat(MenuItemMark.isMarked(null))
                .as("a null slot reads unmarked so the sweep skips it")
                .isFalse();
    }

    @Test
    void airIsNotMarked() {
        assertThat(MenuItemMark.isMarked(new ItemStack(Material.AIR)))
                .as("an empty (AIR) slot reads unmarked so the sweep skips it")
                .isFalse();
    }

    @Test
    void markingDoesNotAlterTheMaterialOrAmount() {
        ItemStack marked = MenuItemMark.mark(new ItemStack(Material.DIAMOND, 5));

        assertThat(marked.getType()).isEqualTo(Material.DIAMOND);
        assertThat(marked.getAmount())
                .as("marking only adds a PDC byte, nothing else changes")
                .isEqualTo(5);
    }
}
