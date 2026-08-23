package com.uxplima.uxmessentials.tablist.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** An inclusive, positive slot range used by a virtual tab-list player group. */
public record TablistSlotRange(int first, int last) {

    public TablistSlotRange {
        if (first <= 0) {
            throw new IllegalArgumentException("a tablist slot range must start above zero, got " + first);
        }
        if (last < first) {
            throw new IllegalArgumentException(
                    "a tablist slot range cannot end before it starts: " + first + "-" + last);
        }
    }

    /** Parse either one slot ({@code 7}) or an inclusive range ({@code 7-12}). */
    public static TablistSlotRange parse(String source) {
        String value = Objects.requireNonNull(source, "source").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("a tablist slot range cannot be blank");
        }
        int separator = value.indexOf('-');
        if (separator < 0) {
            int slot = parseSlot(value, source);
            return new TablistSlotRange(slot, slot);
        }
        if (separator != value.lastIndexOf('-')) {
            throw invalid(source);
        }
        int first = parseSlot(value.substring(0, separator).trim(), source);
        int last = parseSlot(value.substring(separator + 1).trim(), source);
        return new TablistSlotRange(first, last);
    }

    /** Expand this range in stable ascending order. */
    public List<Integer> slots() {
        List<Integer> result = new ArrayList<>(last - first + 1);
        for (int slot = first; slot <= last; slot++) {
            result.add(slot);
        }
        return List.copyOf(result);
    }

    public int size() {
        return last - first + 1;
    }

    public boolean contains(int slot) {
        return slot >= first && slot <= last;
    }

    private static int parseSlot(String value, String source) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw invalid(source);
        }
    }

    private static IllegalArgumentException invalid(String source) {
        return new IllegalArgumentException("invalid tablist slot range '" + source + "'; expected N or N-M");
    }
}
