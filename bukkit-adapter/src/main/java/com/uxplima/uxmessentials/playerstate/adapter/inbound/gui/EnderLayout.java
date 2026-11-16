package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The slot order of the {@code /endersee} window, a flat 27-slot ender chest, and how it maps onto a live player. The
 * ender-chest counterpart of {@link InvseeLayout}; every read and write clones.
 */
@NullMarked
final class EnderLayout {

    static final int SIZE = 27;

    private EnderLayout() {}

    /** Snapshot a live player's ender chest into a flat {@value #SIZE}-slot array (cloned). */
    static @Nullable ItemStack[] fromPlayer(Player target) {
        Objects.requireNonNull(target, "target");
        Inventory chest = target.getEnderChest();
        @Nullable ItemStack[] slots = new ItemStack[SIZE];
        for (int slot = 0; slot < SIZE && slot < chest.getSize(); slot++) {
            slots[slot] = clone(chest.getItem(slot));
        }
        return slots;
    }

    /** The item in {@code slot} of {@code target}'s live ender chest. Read on the target's own thread. */
    static @Nullable ItemStack liveSlot(Player target, int slot) {
        return target.getEnderChest().getItem(slot);
    }

    /** Put {@code item} in {@code slot} of {@code target}'s live ender chest. Run on the target's own thread. */
    static void setLiveSlot(Player target, int slot, @Nullable ItemStack item) {
        target.getEnderChest().setItem(slot, clone(item));
    }

    private static @Nullable ItemStack clone(@Nullable ItemStack stack) {
        return stack == null || stack.getType().isAir() ? null : stack.clone();
    }
}
