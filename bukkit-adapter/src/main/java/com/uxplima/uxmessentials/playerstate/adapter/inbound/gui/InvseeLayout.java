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

    /** Copy {@code target}'s live items into {@code menu}, cloning each so the menu never aliases a live stack. */
    static void seed(Inventory menu, Player target) {
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(target, "target");
        PlayerInventory live = target.getInventory();
        @Nullable ItemStack[] main = live.getContents();
        for (int slot = 0; slot < MAIN_SLOTS; slot++) {
            menu.setItem(slot, clone(slot < main.length ? main[slot] : null));
        }
        @Nullable ItemStack[] armour = live.getArmorContents();
        for (int i = 0; i < armour.length && ARMOUR_START + i < ARMOUR_END; i++) {
            menu.setItem(ARMOUR_START + i, clone(armour[i]));
        }
        menu.setItem(OFFHAND_SLOT, clone(live.getItemInOffHand()));
        ItemStack pane = filler();
        for (int slot = FILLER_START; slot < SIZE; slot++) {
            menu.setItem(slot, pane.clone());
        }
    }

    /** Reconcile {@code menu}'s editable region back onto {@code target}; filler slots are ignored. */
    static void writeBack(Inventory menu, Player target) {
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(target, "target");
        PlayerInventory live = target.getInventory();
        for (int slot = 0; slot < MAIN_SLOTS; slot++) {
            live.setItem(slot, clone(menu.getItem(slot)));
        }
        @Nullable ItemStack[] armour = new ItemStack[ARMOUR_END - ARMOUR_START];
        for (int i = 0; i < armour.length; i++) {
            armour[i] = clone(menu.getItem(ARMOUR_START + i));
        }
        live.setArmorContents(armour);
        ItemStack offhand = clone(menu.getItem(OFFHAND_SLOT));
        live.setItemInOffHand(offhand == null ? new ItemStack(Material.AIR) : offhand);
    }

    private static @Nullable ItemStack clone(@Nullable ItemStack stack) {
        return stack == null || stack.getType().isAir() ? null : stack.clone();
    }
}
