package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import java.util.Map;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;

/**
 * The raw-NBT capability the menu engine reaches through the optional NBT-API hook. Item meta is built from
 * native Paper data-components first; this seam exists only for the few raw-NBT tags that native components
 * cannot express (the DeluxeMenus-style {@code nbt_string}/{@code nbt_int} edge cases), so Phase-1 item-meta
 * calls it last and only for those tags. When NBT-API is absent the whole capability is the no-op
 * {@link #ABSENT}: the raw-NBT edit is simply skipped and the item is returned unchanged.
 *
 * <p>Each value's tag type is inferred from its text: a value that parses as an integer is written as an int
 * tag, otherwise one that parses as a decimal is written as a double tag, otherwise it is written as a string
 * tag. That mirrors how an operator writes {@code custom-nbt: { Foo: "bar", Level: "3", Weight: "1.5" }} and
 * expects {@code Foo}/{@code Level}/{@code Weight} to land as string/int/double respectively.
 *
 * <p>NBT-API works on the item on the calling thread with no I/O, so a caller invokes this on the viewer's
 * entity thread — the menu engine's item-build and click chains already run there. This interface and its
 * {@link #ABSENT} default reference none of {@code de.tr7zw}; the only class that touches NBT-API is the real
 * applier behind {@link NbtApiHook#whenPresent}, and even that reaches it purely by reflection. So an
 * NBT-API-less server loads no SDK class when resolving this capability.
 */
@NullMarked
public interface NbtApplier {

    /** Whether a live NBT-API is present and usable — false for the absent default. */
    boolean available();

    /**
     * Apply each entry of {@code rawNbt} as an NBT tag to a copy of {@code item} and return that copy; the
     * passed item is never mutated. Each value's type is inferred — integer text becomes an int tag, decimal
     * text a double tag, anything else a string tag. An empty map (or the absent no-op) returns {@code item}
     * unchanged. A failure to reach NBT-API degrades to returning the item unchanged rather than throwing, so
     * a raw-NBT edit can never break a menu click.
     */
    ItemStack apply(ItemStack item, Map<String, String> rawNbt);

    /**
     * The safe no-op used when NBT-API is absent: never available, and {@link #apply} returns the same item
     * unchanged. Native-first item meta means an absent NBT-API simply skips the raw-NBT step. This default
     * carries no {@code de.tr7zw} type, so loading it on an NBT-API-less server pulls in no SDK class.
     */
    NbtApplier ABSENT = new NbtApplier() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public ItemStack apply(ItemStack item, Map<String, String> rawNbt) {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(rawNbt, "rawNbt");
            return item;
        }
    };
}
