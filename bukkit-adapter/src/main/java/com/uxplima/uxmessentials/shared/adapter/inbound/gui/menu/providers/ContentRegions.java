package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

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
 * The slot arithmetic a feature needs around its own {@code content {}} regions: finding a region's declared slots
 * in a parsed spec, reading those slots out of a live window into a positional array, emptying them again, and
 * turning such an array back into the list {@link ContentProvider#render} paints from.
 *
 * <p>A region's geometry lives in its spec file, so every helper here works off the slot list the parsed region
 * declares rather than off any layout assumed in code. Every read copies, so a feature's array never aliases the
 * stacks sitting in the open window.
 */
@NullMarked
public final class ContentRegions {

    private ContentRegions() {}

    /**
     * The declared slots of {@code regionId} in {@code spec}, in the order the file lists them. A spec that is
     * missing the region cannot hold the items the feature means to put there, so this fails loudly at wiring time
     * naming {@code resource}, rather than opening a window with nowhere to put anything.
     */
    public static List<Integer> slots(MenuSpec spec, String regionId, String resource) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(regionId, "regionId");
        ContentRegionSpec region = spec.contents().get(regionId);
        if (region == null) {
            throw new IllegalStateException(
                    resource + ": no content region '" + regionId + "', so the window has nowhere to hold items");
        }
        return region.slots().slots();
    }

    /** Read {@code slots} into a positional array of copies, one entry per slot, null where the slot is empty. */
    public static @Nullable ItemStack[] read(Inventory inv, List<Integer> slots) {
        Objects.requireNonNull(inv, "inv");
        Objects.requireNonNull(slots, "slots");
        @Nullable ItemStack[] read = new ItemStack[slots.size()];
        for (int index = 0; index < slots.size(); index++) {
            read[index] = copyOf(inv.getItem(slots.get(index)));
        }
        return read;
    }

    /** Empty every slot of the region, so whatever was in it leaves the window exactly once. */
    public static void clear(Inventory inv, List<Integer> slots) {
        Objects.requireNonNull(inv, "inv");
        Objects.requireNonNull(slots, "slots");
        for (int slot : slots) {
            inv.setItem(slot, null);
        }
    }

    /** {@code stacks} as a region-ordered list of {@code size} entries, padded with nulls and copied defensively. */
    public static List<@Nullable ItemStack> copies(@Nullable ItemStack @Nullable [] stacks, int size) {
        List<@Nullable ItemStack> painted = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            painted.add(stacks != null && index < stacks.length ? copyOf(stacks[index]) : null);
        }
        return painted;
    }

    /** {@code contents} as the positional array a feature reconciles from, one entry per region slot. */
    public static @Nullable ItemStack[] toArray(List<@Nullable ItemStack> contents, int size) {
        Objects.requireNonNull(contents, "contents");
        @Nullable ItemStack[] slots = new ItemStack[size];
        for (int index = 0; index < size && index < contents.size(); index++) {
            slots[index] = copyOf(contents.get(index));
        }
        return slots;
    }

    /** The stack read back from a window slot: a copy, or null for air, so an empty slot never reads as an item. */
    public static @Nullable ItemStack copyOf(@Nullable ItemStack stack) {
        return stack == null || stack.getType().isAir() ? null : stack.clone();
    }
}
