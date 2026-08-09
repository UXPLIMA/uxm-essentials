package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One movement a viewer is attempting inside a content region, put to that region's {@link ContentProvider} before
 * the engine lets vanilla perform it. It carries where the movement lands, what is being moved and what is already
 * there, so a provider can allow taking an item out while refusing to put one in (or the reverse) without reading
 * the Bukkit event itself.
 *
 * @param slot the raw slot of the window the click landed on
 * @param index the position of that slot within the region's declared order
 * @param kind what the viewer is doing to the region
 * @param cursor the stack on the viewer's cursor, or null when their cursor is empty
 * @param current the stack already in the slot, or null when the slot is empty
 */
@NullMarked
public record ContentClick(
        int slot,
        int index,
        Kind kind,
        @Nullable ItemStack cursor,
        @Nullable ItemStack current) {

    /** What a viewer is doing to a content region. */
    public enum Kind {
        /** Putting an item into the region: a click with a loaded cursor, or a shift-click from their inventory. */
        INSERT,
        /** Taking an item out of the region: a click on a filled slot with an empty cursor, or a shift-click out. */
        TAKE,
        /** Exchanging the slot's stack for the cursor's in one gesture. */
        SWAP
    }

    public ContentClick {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0: " + index);
        }
        Objects.requireNonNull(kind, "kind");
    }

    /** The stack being moved into the region, present only for an {@link Kind#INSERT} or {@link Kind#SWAP}. */
    public Optional<ItemStack> inserted() {
        return kind == Kind.TAKE ? Optional.empty() : Optional.ofNullable(cursor);
    }

    /** The stack being moved out of the region, present only when the slot actually held something. */
    public Optional<ItemStack> taken() {
        return kind == Kind.INSERT ? Optional.empty() : Optional.ofNullable(current);
    }
}
