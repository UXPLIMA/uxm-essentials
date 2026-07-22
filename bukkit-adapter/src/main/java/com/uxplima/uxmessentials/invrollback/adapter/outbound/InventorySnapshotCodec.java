package com.uxplima.uxmessentials.invrollback.adapter.outbound;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntConsumer;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption layer between a player's live inventory ({@code ItemStack[]}) and the opaque {@code byte[]}
 * a {@code Snapshot} stores. This is the only place the invrollback context touches an {@code ItemStack}; the
 * {@code :core} layer never sees one. It mirrors the vaults item-serialization approach: a slot-indexed stream
 * over Paper's {@link ItemStack#serializeAsBytes()} / {@link ItemStack#deserializeBytes(byte[])}, which
 * round-trips the full item (components, enchantments, custom name).
 *
 * <p>A snapshot carries two sections so a restore can rebuild both stores in their own slots: the main inventory
 * (which on a player already spans the hotbar, storage, armor, and offhand) and, optionally, the ender chest.
 * Each section writes a slot count, then for each occupied slot its index and the item's own serialized bytes,
 * closed by a {@code -1} sentinel: empty slots write nothing, so a sparse inventory stays compact.
 *
 * <p>A snapshot also records <em>where</em> it was captured. The location lives inside this payload, not in a DB
 * column, so adding it needs no schema migration. A location-bearing payload opens with a negative
 * {@link #LOCATION_FORMAT_MARKER} sentinel followed by a format version; an older location-less payload opens with
 * its first section's (non-negative) slot count, so the two shapes are told apart by the sign of the first int and
 * an old blob still decodes cleanly to an {@link Optional#empty()} location.
 */
@NullMarked
public final class InventorySnapshotCodec {

    /** The end-of-section sentinel written after the last occupied slot. */
    private static final int END_OF_SECTION = -1;

    /**
     * The negative sentinel that opens a location-bearing payload. An old (location-less) payload opens with a
     * section slot count, which is always {@code >= 0}, so any negative first int marks the newer versioned shape.
     */
    private static final int LOCATION_FORMAT_MARKER = 0xC0DE_0001;

    /** The versioned-payload format version, bumped only if the header layout after the marker ever changes. */
    private static final int FORMAT_VERSION = 1;

    /** The first armor slot in a player's {@code getContents()} array (boots), inclusive. */
    private static final int ARMOR_START = 36;

    /** The offhand slot in a player's {@code getContents()} array; armor is {@code [ARMOR_START, OFFHAND_SLOT)}. */
    private static final int OFFHAND_SLOT = 40;

    private static final ItemStack[] EMPTY = new ItemStack[0];

    private InventorySnapshotCodec() {}

    /** Serialize the main inventory and (optionally) the ender chest into one opaque, location-less payload. */
    public static byte[] encode(@Nullable ItemStack[] contents, @Nullable ItemStack @Nullable [] enderChest) {
        return encode(contents, enderChest, null);
    }

    /**
     * Serialize the main inventory, (optionally) the ender chest, and (optionally) the capture location into one
     * opaque payload. A {@code null} location writes an absent-location marker, so a snapshot taken where no
     * location was available still round-trips.
     */
    public static byte[] encode(
            @Nullable ItemStack[] contents, @Nullable ItemStack @Nullable [] enderChest, @Nullable Position location) {
        Objects.requireNonNull(contents, "contents");
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(LOCATION_FORMAT_MARKER);
            out.writeInt(FORMAT_VERSION);
            writeLocation(out, location);
            writeSection(out, contents);
            writeSection(out, enderChest == null ? EMPTY : enderChest);
            return bytes.toByteArray();
        } catch (IOException io) {
            throw new UncheckedIOException("inventory snapshot could not be serialized", io);
        }
    }

    /** Deserialize a payload produced by {@link #encode} back into its two slot-indexed sections and location. */
    public static Decoded decode(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        if (payload.length == 0) {
            return new Decoded(EMPTY, EMPTY, Optional.empty());
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int first = in.readInt();
            Optional<Position> location = Optional.empty();
            int mainLength = first;
            if (first == LOCATION_FORMAT_MARKER) {
                in.readInt(); // format version; only one shape exists today
                location = Optional.ofNullable(readLocation(in));
                mainLength = in.readInt();
            }
            ItemStack[] contents = readSection(in, mainLength);
            ItemStack[] enderChest = readSection(in, in.readInt());
            return new Decoded(contents, enderChest, location);
        } catch (IOException io) {
            throw new UncheckedIOException("inventory snapshot could not be deserialized", io);
        }
    }

    /**
     * Read just the capture location and the per-store occupied-slot counts from a payload, without deserializing
     * a single {@link ItemStack}. It walks the same stream {@link #decode} does but skips each item's bytes, so it
     * is cheap and safe to call off any tick thread (it touches no Bukkit registry) to build the restore list's
     * info lore.
     */
    public static Summary summarize(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        if (payload.length == 0) {
            return Summary.empty();
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int first = in.readInt();
            Optional<Position> location = Optional.empty();
            if (first == LOCATION_FORMAT_MARKER) {
                in.readInt(); // format version
                location = Optional.ofNullable(readLocation(in));
                in.readInt(); // main section slot count; the walk reads to the sentinel, not by count
            }
            // In the old shape 'first' was the main section slot count, already consumed above.
            int[] main = new int[3]; // items, armor, offhand
            walkSection(in, slot -> bucket(main, slot));
            int[] ender = new int[1];
            in.readInt(); // ender section slot count
            walkSection(in, slot -> ender[0]++);
            return new Summary(location, main[0], main[1], main[2], ender[0]);
        } catch (IOException io) {
            throw new UncheckedIOException("inventory snapshot could not be summarized", io);
        }
    }

    private static void writeLocation(DataOutputStream out, @Nullable Position location) throws IOException {
        if (location == null) {
            out.writeBoolean(false);
            return;
        }
        WorldRef world = location.world();
        out.writeBoolean(true);
        out.writeLong(world.uid().getMostSignificantBits());
        out.writeLong(world.uid().getLeastSignificantBits());
        out.writeUTF(world.name());
        out.writeDouble(location.x());
        out.writeDouble(location.y());
        out.writeDouble(location.z());
        out.writeFloat(location.yaw());
        out.writeFloat(location.pitch());
    }

    private static @Nullable Position readLocation(DataInputStream in) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        UUID uid = new UUID(in.readLong(), in.readLong());
        String world = in.readUTF();
        double x = in.readDouble();
        double y = in.readDouble();
        double z = in.readDouble();
        float yaw = in.readFloat();
        float pitch = in.readFloat();
        return new Position(new WorldRef(uid, world), x, y, z, yaw, pitch);
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

    private static ItemStack[] readSection(DataInputStream in, int rawLength) throws IOException {
        int length = Math.max(0, rawLength);
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
     * Walk a section's occupied slots without deserializing the items, invoking {@code onSlot} for each. The
     * section's slot-count header is consumed by the caller; this reads to the {@link #END_OF_SECTION} sentinel.
     */
    private static void walkSection(DataInputStream in, IntConsumer onSlot) throws IOException {
        int slot;
        while ((slot = in.readInt()) != END_OF_SECTION) {
            in.readNBytes(in.readInt());
            if (slot >= 0) {
                onSlot.accept(slot);
            }
        }
    }

    private static void bucket(int[] counts, int slot) {
        if (slot >= ARMOR_START && slot < OFFHAND_SLOT) {
            counts[1]++;
        } else if (slot == OFFHAND_SLOT) {
            counts[2]++;
        } else {
            counts[0]++;
        }
    }

    /**
     * The two stores a snapshot holds, plus where it was taken: the main inventory (hotbar, storage, armor,
     * offhand), the ender chest (a zero-length array when the snapshot did not include one), and the capture
     * {@link Position} ({@link Optional#empty()} for a snapshot taken before location capture shipped). A plain
     * carrier rather than a record because its item components are arrays: the codec creates them once and hands
     * them straight to the caller (the restore flow), so no defensive copy is warranted.
     */
    public static final class Decoded {

        private final ItemStack[] contents;
        private final ItemStack[] enderChest;
        private final Optional<Position> location;

        Decoded(ItemStack[] contents, ItemStack[] enderChest, Optional<Position> location) {
            this.contents = contents;
            this.enderChest = enderChest;
            this.location = Objects.requireNonNull(location, "location");
        }

        /** The main inventory slots (hotbar, storage, armor, offhand). */
        public ItemStack[] contents() {
            return contents;
        }

        /** The ender chest slots, or a zero-length array when none was captured. */
        public ItemStack[] enderChest() {
            return enderChest;
        }

        /** Where the snapshot was captured, or empty for a snapshot taken before location capture. */
        public Optional<Position> location() {
            return location;
        }
    }

    /**
     * A lightweight, item-free view of a payload: the capture {@link Position} (when recorded) and the count of
     * occupied slots in each store. Backs the restore GUI's info lore, which needs the where and the how-many but
     * not the items themselves.
     *
     * @param location where the snapshot was captured, or empty for a pre-location snapshot
     * @param items occupied hotbar and storage slots (the main inventory, excluding armor and offhand)
     * @param armor occupied armor slots (helmet, chestplate, leggings, boots)
     * @param offhand occupied offhand slots (0 or 1)
     * @param ender occupied ender-chest slots
     */
    public record Summary(Optional<Position> location, int items, int armor, int offhand, int ender) {

        public Summary {
            Objects.requireNonNull(location, "location");
        }

        static Summary empty() {
            return new Summary(Optional.empty(), 0, 0, 0, 0);
        }

        /** Items the operator thinks of as "carried": the main inventory plus whatever is in the offhand. */
        public int carriedItems() {
            return items + offhand;
        }
    }
}
