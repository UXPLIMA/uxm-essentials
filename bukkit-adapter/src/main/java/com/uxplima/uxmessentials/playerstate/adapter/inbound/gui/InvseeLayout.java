package com.uxplima.uxmessentials.playerstate.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The fixed slot map of the {@code /invsee} menu, plus the two reconciliation directions over it. A 6-row
 * (54-slot) chest is laid out so the editable region matches what a player actually carries:
 *
 * <pre>
 *   slots  0..35  -> the target's main inventory (hotbar 0..8 then storage 9..35, as Bukkit orders it)
 *   slots 36..39  -> armour: 36 boots, 37 leggings, 38 chestplate, 39 helmet
 *   slot     40   -> the offhand item
 *   slots 41..53  -> a non-interactive filler pane (a labelled gray glass pane)
 * </pre>
 *
 * <p>{@link #seed} copies the live player's items <em>into</em> the menu (clones, so the menu never aliases the
 * live stacks); {@link #writeBack} copies the menu's editable region back <em>onto</em> the live player. The two
 * are mirror images, which keeps the layout in exactly one place and lets a test reason about conservation. The
 * armour order matches Bukkit's {@link PlayerInventory#getArmorContents()} (index 0 boots … 3 helmet).
 */
@NullMarked
final class InvseeLayout {

    static final int SIZE = 54;
    static final int MAIN_SLOTS = 36;
    static final int ARMOUR_START = 36;
    static final int ARMOUR_END = 40; // exclusive
    static final int OFFHAND_SLOT = 40;
    static final int FILLER_START = 41; // 41..53 inclusive are filler

    /** Main (0..35) + armour (36..39) + offhand (40): the editable slot span, also the flat-array length. */
    static final int SLOT_COUNT = OFFHAND_SLOT + 1;

    private InvseeLayout() {}

    /** Whether {@code slot} is one the viewer may edit (a mirror of a real item slot), not filler. */
    static boolean isEditable(int slot) {
        return slot >= 0 && slot <= OFFHAND_SLOT;
    }

    /** The gray-glass filler with no name, used to pad the slots that map to nothing on the target. */
    static ItemStack filler() {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
    }

    /** Reconcile {@code menu}'s editable region back onto {@code target}; filler slots are ignored. */
    static void writeBack(Inventory menu, Player target) {
        Objects.requireNonNull(target, "target");
        applySlots(readSlots(menu), target);
    }

    /** Snapshot a live player's main/armour/offhand into a flat {@value #SLOT_COUNT}-slot array (cloned). */
    static @Nullable ItemStack[] fromPlayer(Player target) {
        Objects.requireNonNull(target, "target");
        PlayerInventory live = target.getInventory();
        @Nullable ItemStack[] slots = new ItemStack[SLOT_COUNT];
        @Nullable ItemStack[] main = live.getContents();
        for (int slot = 0; slot < MAIN_SLOTS; slot++) {
            slots[slot] = clone(slot < main.length ? main[slot] : null);
        }
        @Nullable ItemStack[] armour = live.getArmorContents();
        for (int i = 0; i < armour.length && ARMOUR_START + i < ARMOUR_END; i++) {
            slots[ARMOUR_START + i] = clone(armour[i]);
        }
        slots[OFFHAND_SLOT] = clone(live.getItemInOffHand());
        return slots;
    }

    /** Copy a flat {@value #SLOT_COUNT}-slot array into {@code menu} (slots 0..40) and pad the filler region. */
    static void seedSlots(Inventory menu, @Nullable ItemStack[] slots) {
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(slots, "slots");
        for (int slot = 0; slot <= OFFHAND_SLOT; slot++) {
            menu.setItem(slot, clone(at(slots, slot)));
        }
        ItemStack pane = filler();
        for (int slot = FILLER_START; slot < SIZE; slot++) {
            menu.setItem(slot, pane.clone());
        }
    }

    /** Read {@code menu}'s editable region (slots 0..40) into a flat {@value #SLOT_COUNT}-slot array (cloned). */
    static @Nullable ItemStack[] readSlots(Inventory menu) {
        Objects.requireNonNull(menu, "menu");
        @Nullable ItemStack[] slots = new ItemStack[SLOT_COUNT];
        for (int slot = 0; slot <= OFFHAND_SLOT; slot++) {
            slots[slot] = clone(menu.getItem(slot));
        }
        return slots;
    }

    /** Reconcile a flat {@value #SLOT_COUNT}-slot array onto {@code target}'s live inventory. */
    static void applySlots(@Nullable ItemStack[] slots, Player target) {
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(target, "target");
        PlayerInventory live = target.getInventory();
        for (int slot = 0; slot < MAIN_SLOTS; slot++) {
            live.setItem(slot, clone(at(slots, slot)));
        }
        @Nullable ItemStack[] armour = new ItemStack[ARMOUR_END - ARMOUR_START];
        for (int i = 0; i < armour.length; i++) {
            armour[i] = clone(at(slots, ARMOUR_START + i));
        }
        live.setArmorContents(armour);
        ItemStack offhand = clone(at(slots, OFFHAND_SLOT));
        live.setItemInOffHand(offhand == null ? new ItemStack(Material.AIR) : offhand);
    }

    private static @Nullable ItemStack at(@Nullable ItemStack[] slots, int index) {
        return index < slots.length ? slots[index] : null;
    }

    private static @Nullable ItemStack clone(@Nullable ItemStack stack) {
        return stack == null || stack.getType().isAir() ? null : stack.clone();
    }
}
