package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The slot order of the {@code /invsee} window and how it maps onto a live player:
 *
 * <pre>
 *   slots  0..35  -> the main inventory (hotbar 0..8 then storage 9..35, as Bukkit orders it)
 *   slots 36..39  -> armour: 36 boots, 37 leggings, 38 chestplate, 39 helmet
 *   slot     40   -> the offhand item
 * </pre>
 *
 * <p>The same order numbers a stored inventory, so a live and an offline window line up slot for slot. Every read
 * and write clones, so a window never shares a stack with the live inventory.
 */
@NullMarked
final class InvseeLayout {

    static final int MAIN_SLOTS = 36;
    static final int ARMOUR_START = 36;
    static final int ARMOUR_END = 40; // exclusive
    static final int OFFHAND_SLOT = 40;

    /** Main (0..35) + armour (36..39) + offhand (40): the window's item slots, also the flat-array length. */
    static final int SLOT_COUNT = OFFHAND_SLOT + 1;

    private InvseeLayout() {}

    /** Snapshot a live player's main/armour/offhand into a flat {@value #SLOT_COUNT}-slot array (cloned). */
    static @Nullable ItemStack[] fromPlayer(Player target) {
        Objects.requireNonNull(target, "target");
        @Nullable ItemStack[] slots = new ItemStack[SLOT_COUNT];
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            slots[slot] = clone(liveSlot(target, slot));
        }
        return slots;
    }

    /** The item in {@code slot}, as this layout numbers it, of {@code target}'s live inventory. */
    static @Nullable ItemStack liveSlot(Player target, int slot) {
        PlayerInventory live = target.getInventory();
        return switch (slot) {
            case ARMOUR_START -> live.getBoots();
            case ARMOUR_START + 1 -> live.getLeggings();
            case ARMOUR_START + 2 -> live.getChestplate();
            case ARMOUR_START + 3 -> live.getHelmet();
            case OFFHAND_SLOT -> live.getItemInOffHand();
            default -> live.getItem(slot);
        };
    }

    /** Put {@code item} in {@code slot}, as this layout numbers it, of {@code target}'s live inventory. */
    static void setLiveSlot(Player target, int slot, @Nullable ItemStack item) {
        PlayerInventory live = target.getInventory();
        ItemStack copy = clone(item);
        switch (slot) {
            case ARMOUR_START -> live.setBoots(copy);
            case ARMOUR_START + 1 -> live.setLeggings(copy);
            case ARMOUR_START + 2 -> live.setChestplate(copy);
            case ARMOUR_START + 3 -> live.setHelmet(copy);
            case OFFHAND_SLOT -> live.setItemInOffHand(copy == null ? new ItemStack(Material.AIR) : copy);
            default -> live.setItem(slot, copy);
        }
    }

    private static @Nullable ItemStack clone(@Nullable ItemStack stack) {
        return stack == null || stack.getType().isAir() ? null : stack.clone();
    }
}
