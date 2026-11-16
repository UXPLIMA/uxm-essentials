package com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.RawNbt.Entry;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.RawNbt.ListTag;
import org.jspecify.annotations.Nullable;

/**
 * Where a container's slots live in a player data file, numbered the way the {@code /invsee} and {@code /endersee}
 * windows lay them out. The main inventory and the ender chest are lists of item compounds that each carry a
 * {@code Slot} byte; armour and the offhand are entries of the {@code equipment} compound, keyed by the slot name.
 *
 * <p>A slot is read as the payload the file stores for it, so a save can tell whether it still holds what the window
 * was opened with. A save rewrites only the slots it changes: every other list element, equipment entry and tag is
 * written back as it was read.
 */
public final class ContainerLayout {

    /** Slots 0..35 the main inventory, 36..39 boots to helmet, 40 the offhand. */
    public static final ContainerLayout INVENTORY =
            new ContainerLayout("inventory", "Inventory", 36, List.of("feet", "legs", "chest", "head", "offhand"));

    /** Slots 0..26 the ender chest. */
    public static final ContainerLayout ENDER = new ContainerLayout("ender chest", "EnderItems", 27, List.of());

    private static final String SLOT = "Slot";
    private static final int NO_SLOT = -1;
    private static final String EQUIPMENT = "equipment";

    private final String name;
    private final String listKey;
    private final int listSlots;
    private final List<String> equipment;

    private ContainerLayout(String name, String listKey, int listSlots, List<String> equipment) {
        this.name = name;
        this.listKey = listKey;
        this.listSlots = listSlots;
        this.equipment = List.copyOf(equipment);
    }

    /** How many slots the container has. */
    public int size() {
        return listSlots + equipment.size();
    }

    /** The stored payload of every filled slot. A slot the file lists twice reads as its last entry, as on login. */
    Map<Integer, TagPayload> slots(List<Entry> root) throws IOException {
        Map<Integer, TagPayload> slots = new HashMap<>();
        Entry list = find(root, listKey);
        if (list != null) {
            for (TagPayload element : elements(list)) {
                int slot = slotOf(element);
                if (slot >= 0 && slot < listSlots) {
                    slots.put(slot, element);
                }
            }
        }
        Entry worn = equipment.isEmpty() ? null : find(root, EQUIPMENT);
        if (worn != null) {
            for (Entry entry : compoundEntries(worn)) {
                int index = equipment.indexOf(entry.name());
                if (index >= 0) {
                    requireCompound(entry);
                    slots.put(listSlots + index, entry.payload());
                }
            }
        }
        return slots;
    }

    /** {@code root} with {@code writes} applied and everything else as it was. */
    List<Entry> apply(List<Entry> root, List<SlotWrite> writes) throws IOException {
        Map<Integer, @Nullable TagPayload> listed = new HashMap<>();
        Map<String, @Nullable TagPayload> worn = new HashMap<>();
        for (SlotWrite write : writes) {
            if (write.slot() < 0 || write.slot() >= size()) {
                throw new IllegalArgumentException("slot " + write.slot() + " is outside the " + name);
            }
            if (write.slot() < listSlots) {
                listed.put(write.slot(), write.item());
            } else {
                worn.put(equipment.get(write.slot() - listSlots), write.item());
            }
        }
        List<Entry> result = new ArrayList<>(root);
        if (!listed.isEmpty()) {
            rewriteList(result, listed);
        }
        if (!worn.isEmpty()) {
            rewriteEquipment(result, worn);
        }
        return result;
    }

    @Override
    public String toString() {
        return name;
    }

    private void rewriteList(List<Entry> root, Map<Integer, @Nullable TagPayload> writes) throws IOException {
        Entry old = find(root, listKey);
        List<TagPayload> elements = old == null ? List.of() : elements(old);
        // A changed slot's new item takes the place of the slot's last entry; earlier duplicates go.
        Map<Integer, Integer> last = new HashMap<>();
        for (int i = 0; i < elements.size(); i++) {
            int slot = slotOf(elements.get(i));
            if (writes.containsKey(slot)) {
                last.put(slot, i);
            }
        }
        List<TagPayload> kept = new ArrayList<>(elements.size() + writes.size());
        Set<Integer> placed = new HashSet<>();
        for (int i = 0; i < elements.size(); i++) {
            TagPayload element = elements.get(i);
            int slot = slotOf(element);
            if (!writes.containsKey(slot)) {
                kept.add(element);
                continue;
            }
            TagPayload item = writes.get(slot);
            if (item != null && Integer.valueOf(i).equals(last.get(slot))) {
                kept.add(withSlot(item, slot));
                placed.add(slot);
            }
        }
        for (Map.Entry<Integer, @Nullable TagPayload> write : sorted(writes)) {
            TagPayload item = write.getValue();
            if (item != null && !placed.contains(write.getKey())) {
                kept.add(withSlot(item, write.getKey()));
            }
        }
        put(root, new Entry(RawNbt.LIST, listKey, RawNbt.list(new ListTag(RawNbt.COMPOUND, kept))));
    }

    private static void rewriteEquipment(List<Entry> root, Map<String, @Nullable TagPayload> writes)
            throws IOException {
        Entry old = find(root, EQUIPMENT);
        List<Entry> entries = old == null ? new ArrayList<>() : new ArrayList<>(compoundEntries(old));
        for (Map.Entry<String, @Nullable TagPayload> write : writes.entrySet()) {
            TagPayload item = write.getValue();
            if (item == null) {
                entries.removeIf(entry -> entry.name().equals(write.getKey()));
            } else {
                put(entries, new Entry(RawNbt.COMPOUND, write.getKey(), item));
            }
        }
        if (entries.isEmpty()) {
            // The server leaves the key out when nothing is worn, so this does too.
            root.removeIf(entry -> entry.name().equals(EQUIPMENT));
        } else {
            put(root, new Entry(RawNbt.COMPOUND, EQUIPMENT, RawNbt.compound(entries)));
        }
    }

    private static List<Map.Entry<Integer, @Nullable TagPayload>> sorted(Map<Integer, @Nullable TagPayload> writes) {
        List<Map.Entry<Integer, @Nullable TagPayload>> sorted = new ArrayList<>(writes.entrySet());
        sorted.sort(Map.Entry.comparingByKey());
        return sorted;
    }

    private static List<TagPayload> elements(Entry list) throws IOException {
        if (list.type() != RawNbt.LIST) {
            throw new IOException("'" + list.name() + "' is tag type " + list.type() + ", not a list");
        }
        ListTag tag = RawNbt.list(list.payload());
        if (!tag.elements().isEmpty() && tag.elementType() != RawNbt.COMPOUND) {
            throw new IOException("'" + list.name() + "' holds tag type " + tag.elementType() + ", not compounds");
        }
        return tag.elements();
    }

    private static List<Entry> compoundEntries(Entry compound) throws IOException {
        requireCompound(compound);
        return RawNbt.entries(compound.payload());
    }

    private static void requireCompound(Entry entry) throws IOException {
        if (entry.type() != RawNbt.COMPOUND) {
            throw new IOException("'" + entry.name() + "' is tag type " + entry.type() + ", not a compound");
        }
    }

    /**
     * The slot an item compound names, read as the server reads it: any number, narrowed to an unsigned byte, and a
     * missing one is slot 0. A slot that is not a number makes the server skip the entry, so it is {@code NO_SLOT}
     * here: such an entry is never shown and is written back as it was.
     */
    private static int slotOf(TagPayload element) throws IOException {
        Entry slot = find(RawNbt.entries(element), SLOT);
        if (slot == null) {
            return 0;
        }
        Number value = RawNbt.numeric(slot.type(), slot.payload());
        return value == null ? NO_SLOT : Byte.toUnsignedInt(value.byteValue());
    }

    private static TagPayload withSlot(TagPayload item, int slot) throws IOException {
        List<Entry> entries = new ArrayList<>(RawNbt.entries(item));
        entries.removeIf(entry -> entry.name().equals(SLOT));
        entries.addFirst(new Entry(RawNbt.BYTE, SLOT, RawNbt.byteValue(slot)));
        return RawNbt.compound(entries);
    }

    private static @Nullable Entry find(List<Entry> entries, String name) {
        for (Entry entry : entries) {
            if (entry.name().equals(name)) {
                return entry;
            }
        }
        return null;
    }

    /** Replace the entry of the same name where it stands, or add it at the end. */
    private static void put(List<Entry> entries, Entry entry) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).name().equals(entry.name())) {
                entries.set(i, entry);
                return;
            }
        }
        entries.add(entry);
    }
}
