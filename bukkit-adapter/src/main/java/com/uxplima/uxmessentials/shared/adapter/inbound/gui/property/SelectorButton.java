package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;

/**
 * One clickable option in a selector child menu: the slot it sits at, the already-built icon to draw there, and
 * the action to run when the viewer chooses it. The icon is fully prepared by the caller — name, lore, and any
 * selection glint are baked in — so a {@link SelectorOpener} only places it and wires the click; it makes no
 * presentation decision of its own.
 *
 * <p>This is the unit an {@link EnumProperty} (and, later, a list or colour property) hands the opener: a property
 * builds one {@code SelectorButton} per option and lets the engine paint and route them, keeping the property free
 * of the menu runtime it opens into.
 *
 * @param slot the inventory slot the button is drawn at
 * @param icon the prepared icon to place (name/lore/glint already applied)
 * @param onChoose the action run once when the viewer clicks the button
 */
@NullMarked
public record SelectorButton(int slot, ItemStack icon, Runnable onChoose) {

    public SelectorButton {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(onChoose, "onChoose");
    }
}
