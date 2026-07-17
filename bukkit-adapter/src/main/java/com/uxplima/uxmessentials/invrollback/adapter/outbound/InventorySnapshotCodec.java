package com.uxplima.uxmessentials.invrollback.adapter.outbound;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption layer between a player's live inventory ({@code ItemStack[]}) and the opaque {@code byte[]}
 * a {@code Snapshot} stores. This is the only place the invrollback context touches an {@code ItemStack}; the
 * {@code :core} layer never sees one. It mirrors the vaults item-serialization approach — a slot-indexed stream
 * over Paper's {@link ItemStack#serializeAsBytes()} / {@link ItemStack#deserializeBytes(byte[])}, which
 * round-trips the full item (components, enchantments, custom name).
 *
 * <p>A snapshot carries two sections so a restore can rebuild both stores in their own slots: the main inventory
 * (which on a player already spans the hotbar, storage, armor, and offhand) and, optionally, the ender chest.
 * Each section writes a slot count, then for each occupied slot its index and the item's own serialized bytes,
 * closed by a {@code -1} sentinel — empty slots write nothing, so a sparse inventory stays compact.
 */
@NullMarked
public final class InventorySnapshotCodec {

    /** The end-of-section sentinel written after the last occupied slot. */
    private static final int END_OF_SECTION = -1;

    private static final ItemStack[] EMPTY = new ItemStack[0];

    private InventorySnapshotCodec() {}

    /** Serialize the main inventory and (optionally) the ender chest into one opaque payload. */
    public static byte[] encode(@Nullable ItemStack[] contents, @Nullable ItemStack @Nullable [] enderChest) {
        Objects.requireNonNull(contents, "contents");
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes)) {
            writeSection(out, contents);
            writeSection(out, enderChest == null ? EMPTY : enderChest);
            return bytes.toByteArray();
        } catch (IOException io) {
            throw new UncheckedIOException("inventory snapshot could not be serialized", io);
        }
    }

    /** Deserialize a payload produced by {@link #encode} back into its two slot-indexed sections. */
    public static Decoded decode(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            ItemStack[] contents = readSection(in);
            ItemStack[] enderChest = readSection(in);
            return new Decoded(contents, enderChest);
        } catch (IOException io) {
            throw new UncheckedIOException("inventory snapshot could not be deserialized", io);
        }
    }

    private static void writeSection(DataOutputStream out, @Nullable ItemStack[] slots) throws IOException {
        out.writeInt(slots.length);
        for (int slot = 0; slot < slots.length; slot++) {
            writeSlot(out, slot, slots[slot]);
        }
        out.writeInt(END_OF_SECTION);
    }

    private static void writeSlot(DataOutputStream out, int slot, @Nullable ItemStack stack) throws IOException {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        byte[] item = stack.serializeAsBytes();
        out.writeInt(slot);
        out.writeInt(item.length);
        out.write(item);
    }

    private static ItemStack[] readSection(DataInputStream in) throws IOException {
        int length = Math.max(0, in.readInt());
        ItemStack[] slots = new ItemStack[length];
        int slot;
        while ((slot = in.readInt()) != END_OF_SECTION) {
            byte[] item = in.readNBytes(in.readInt());
            if (slot >= 0 && slot < slots.length) {
                slots[slot] = ItemStack.deserializeBytes(item);
            }
        }
        return slots;
    }

    /**
     * The two stores a snapshot holds: the main inventory (hotbar, storage, armor, offhand) and the ender chest
     * (a zero-length array when the snapshot did not include one). A plain carrier rather than a record because
     * its components are arrays — the codec creates them once and hands them straight to the caller (the restore
     * flow), so no defensive copy is warranted.
     */
    public static final class Decoded {

        private final ItemStack[] contents;
        private final ItemStack[] enderChest;

        Decoded(ItemStack[] contents, ItemStack[] enderChest) {
            this.contents = contents;
            this.enderChest = enderChest;
        }

        /** The main inventory slots (hotbar, storage, armor, offhand). */
        public ItemStack[] contents() {
            return contents;
        }

        /** The ender chest slots, or a zero-length array when none was captured. */
        public ItemStack[] enderChest() {
            return enderChest;
        }
    }
}
