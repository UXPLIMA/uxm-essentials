package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * An ordered, de-duplicated set of inventory slots parsed from a spec's compact token form. A token is either a
 * single index ({@code "8"}) or an inclusive range ({@code "0-2"} → 0, 1, 2). Slots keep their first-seen order
 * so a spec author controls the fill sequence; repeats collapse silently. Every resulting index is validated
 * against the menu's inventory size up front, so the renderer never has to bounds-check again.
 */
public record SlotSet(List<Integer> slots) {

    public SlotSet {
        slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
    }

    public static SlotSet parse(List<String> tokens, int size) {
        Objects.requireNonNull(tokens, "tokens");
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive: " + size);
        }
        LinkedHashSet<Integer> ordered = new LinkedHashSet<>();
        for (String token : tokens) {
            addToken(ordered, token.strip(), size);
        }
        return new SlotSet(List.copyOf(ordered));
    }

    private static void addToken(LinkedHashSet<Integer> ordered, String token, int size) {
        int dash = token.indexOf('-');
        if (dash < 0) {
            ordered.add(checkSlot(Integer.parseInt(token), size));
            return;
        }
        int lo = checkSlot(Integer.parseInt(token.substring(0, dash).strip()), size);
        int hi = checkSlot(Integer.parseInt(token.substring(dash + 1).strip()), size);
        for (int slot = lo; slot <= hi; slot++) {
            ordered.add(slot);
        }
    }

    private static int checkSlot(int slot, int size) {
        if (slot < 0 || slot >= size) {
            throw new IllegalArgumentException("slot out of bounds [0," + size + "): " + slot);
        }
        return slot;
    }
}
