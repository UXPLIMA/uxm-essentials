package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The slot map shared by the kit GUIs: a kit stores its stacks in definition order with no per-item slot, so
 * each item is laid out at its list index ({@code items.get(i)} → slot {@code i}), the same one-based numbering
 * the chat preview prints as {@code Slot n}. The menu is sized to the smallest whole number of rows that holds
 * the kit's items, never fewer than one row and never more than six (a 54-slot double chest is Bukkit's chest
 * ceiling), and the slots past the last item are padded with a non-interactive gray-glass pane.
 *
 * <p>{@link #seed} decodes the opaque {@link KitItem} payloads through {@link KitItemCodec} — the one place the
 * kits context turns a stored item back into an {@link ItemStack} — and clones each into the menu so the window
 * never aliases a definition's stack.
 */
@NullMarked
final class KitGuiLayout {

    static final int SLOTS_PER_ROW = 9;
    static final int MAX_ROWS = 6;
    static final int MAX_SLOTS = MAX_ROWS * SLOTS_PER_ROW;

    private KitGuiLayout() {}

    /** The smallest number of rows (1..6) that holds {@code itemCount} items at one item per slot. */
    static int rowsFor(int itemCount) {
        if (itemCount <= 0) {
            return 1;
        }
        int rows = (itemCount + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW;
        return Math.min(rows, MAX_ROWS);
    }

    /**
     * Place {@code items} into {@code menu} at one item per slot in definition order, padding the trailing slots
     * with filler. An item whose index falls past the menu's capacity (a kit larger than 54 stacks) is dropped
     * from the view; the menu can never advertise a slot the chest does not have.
     */
    static void seed(Inventory menu, List<KitItem> items) {
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(items, "items");
        int size = menu.getSize();
        ItemStack pane = filler();
        for (int slot = 0; slot < size; slot++) {
            if (slot < items.size()) {
                menu.setItem(slot, KitItemCodec.decode(items.get(slot)));
            } else {
                menu.setItem(slot, pane.clone());
            }
        }
    }

    /** The gray-glass filler with no name, padding the slots a kit's items do not fill. */
    static ItemStack filler() {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
    }
}
