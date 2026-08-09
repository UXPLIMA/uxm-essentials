package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ContentRegionSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The slot arithmetic both trade windows share: reading a declared block of window slots into the positional array
 * the domain offer is kept as, emptying it again, and turning an offer back into the list a content provider paints.
 * A trade window's geometry comes from its spec file, so these all work off the slot list the parsed region declares
 * rather than off any assumed layout.
 */
@NullMarked
final class TradeRegions {

    private TradeRegions() {}

    /** The declared slots of {@code regionId}; a spec missing that region cannot open a trade, so it fails loudly. */
    static List<Integer> slots(MenuSpec spec, String regionId, String resource) {
        Objects.requireNonNull(spec, "spec");
        ContentRegionSpec region = spec.contents().get(regionId);
        if (region == null) {
            throw new IllegalStateException(
                    resource + ": no content region '" + regionId + "', so the window has nowhere to hold items");
        }
        return region.slots().slots();
    }

    /** Read {@code slots} into a positional array of copies, one entry per slot, null where the slot is empty. */
    static @Nullable ItemStack[] read(Inventory inv, List<Integer> slots) {
        Objects.requireNonNull(inv, "inv");
        @Nullable ItemStack[] offer = new ItemStack[slots.size()];
        for (int index = 0; index < slots.size(); index++) {
            offer[index] = copyOf(inv.getItem(slots.get(index)));
        }
        return offer;
    }

    /** Empty every slot of the region — the offered originals leave the window exactly once. */
    static void clear(Inventory inv, List<Integer> slots) {
        Objects.requireNonNull(inv, "inv");
        for (int slot : slots) {
            inv.setItem(slot, null);
        }
    }

    /** {@code offer} as a region-ordered list of {@code size} entries, padded with nulls and copied defensively. */
    static List<@Nullable ItemStack> copies(@Nullable ItemStack @Nullable [] offer, int size) {
        List<@Nullable ItemStack> painted = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            painted.add(offer != null && index < offer.length ? copyOf(offer[index]) : null);
        }
        return painted;
    }

    /** The stack read back from a window slot: a copy, or null for air, so an empty slot never reads as an item. */
    static @Nullable ItemStack copyOf(@Nullable ItemStack stack) {
        return stack == null || stack.getType().isAir() ? null : stack.clone();
    }
}
