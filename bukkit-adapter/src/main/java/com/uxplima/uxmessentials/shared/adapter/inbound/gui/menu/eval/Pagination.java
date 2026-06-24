package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Slices a flat list of list-source entries into the slots a single rendered page can show. The content slots are
 * the cells a menu reserves for its scrollable list (everything else is fixed decoration), so the page size is
 * simply how many of those slots exist. Entries are laid into those slots in order, and a request for a page past
 * the end is clamped back to the last real page rather than rendering an empty screen. With no entries — or no
 * content slots at all — there is still exactly one page, just an empty one, which keeps the renderer's paging
 * controls consistent.
 */
public final class Pagination {

    private Pagination() {}

    /**
     * A single rendered page: the entry placed in each content slot, the clamped page index, and how many pages
     * the full entry list spans. A short final page leaves its trailing content slots unmapped.
     */
    public record Page<T>(List<Map.Entry<Integer, T>> placements, int page, int pageCount) {}

    /**
     * Computes the placements for one page of {@code entries} across {@code contentSlots}. The page size is the
     * number of content slots; {@code page} is clamped into {@code [0, pageCount - 1]}. An empty slot list yields
     * a single empty page so callers never divide by zero.
     */
    public static <T> Page<T> paginate(List<T> entries, List<Integer> contentSlots, int page) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(contentSlots, "contentSlots");
        int size = contentSlots.size();
        if (size == 0) {
            return new Page<>(List.of(), 0, 1);
        }
        int pageCount = Math.max(1, (entries.size() + size - 1) / size);
        int clamped = Math.max(0, Math.min(page, pageCount - 1));
        return new Page<>(place(entries, contentSlots, size, clamped), clamped, pageCount);
    }

    private static <T> List<Map.Entry<Integer, T>> place(
            List<T> entries, List<Integer> contentSlots, int size, int page) {
        int from = page * size;
        int to = Math.min(from + size, entries.size());
        List<Map.Entry<Integer, T>> placements = new ArrayList<>(to - from);
        for (int i = 0; from + i < to; i++) {
            placements.add(Map.entry(contentSlots.get(i), entries.get(from + i)));
        }
        return placements;
    }
}
