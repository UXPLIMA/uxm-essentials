package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * An offline container as a window was opened on it: the items it showed and the file state they came from. A save
 * of the window changes only the slots whose items differ from these, and only while the file still holds what it
 * held here, which is what keeps two edits from overwriting each other.
 */
public final class OfflineSnapshot {

    private final @Nullable SaveOutcome problem;
    private final @Nullable ItemStack[] items;
    private final @Nullable StoredSlots stored;
    private final boolean editable;

    private OfflineSnapshot(
            @Nullable SaveOutcome problem,
            @Nullable ItemStack[] items,
            @Nullable StoredSlots stored,
            boolean editable) {
        this.problem = problem;
        this.items = items;
        this.stored = stored;
        this.editable = editable;
    }

    /** A container that was read: {@code editable} when every item was understood and the data is current. */
    static OfflineSnapshot found(@Nullable ItemStack[] items, StoredSlots stored, boolean editable) {
        return new OfflineSnapshot(
                null,
                Objects.requireNonNull(items, "items").clone(),
                Objects.requireNonNull(stored, "stored"),
                editable);
    }

    /** A container that could not be read, for {@code reason}. */
    public static OfflineSnapshot unreadable(SaveOutcome reason, ContainerLayout layout) {
        return new OfflineSnapshot(Objects.requireNonNull(reason, "reason"), new ItemStack[layout.size()], null, false);
    }

    /** Why the container could not be read, or empty when it was. */
    public Optional<SaveOutcome> problem() {
        return Optional.ofNullable(problem);
    }

    /** The items, one per slot, as a copy; {@code null} is an empty slot. */
    public @Nullable ItemStack[] items() {
        return items.clone();
    }

    /** Whether a window on this may change the file: the items were all understood and written by this version. */
    public boolean editable() {
        return editable;
    }

    /** The slots where {@code contents} holds something other than this snapshot, in order. */
    public List<Integer> changedSlots(@Nullable ItemStack[] contents) {
        List<Integer> changed = new ArrayList<>();
        for (int slot = 0; slot < items.length; slot++) {
            if (!sameItem(items[slot], slot < contents.length ? contents[slot] : null)) {
                changed.add(slot);
            }
        }
        return changed;
    }

    /** The item this snapshot holds in {@code slot}. */
    public @Nullable ItemStack item(int slot) {
        return items[slot];
    }

    /** Whether two slots hold the same thing: the same item and amount, or nothing at all. */
    public static boolean sameItem(@Nullable ItemStack a, @Nullable ItemStack b) {
        if (a == null || nothing(a)) {
            return b == null || nothing(b);
        }
        return b != null && !nothing(b) && a.equals(b);
    }

    /** Whether {@code item} stands for an empty slot. */
    public static boolean nothing(ItemStack item) {
        return item.getType().isAir() || item.getAmount() <= 0;
    }

    StoredSlots stored() {
        return Objects.requireNonNull(stored, "a snapshot that was not read has nothing to save against");
    }
}
