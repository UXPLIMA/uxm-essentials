package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval;

import java.util.Map;
import java.util.Objects;

/**
 * What a paged list source is asked for: one page of a filtered, sorted corpus. The engine builds it from the
 * viewer's current list state, so the source can push the paging, filtering and sorting down to whatever holds
 * the data instead of receiving the whole corpus and slicing it in memory.
 *
 * <p>{@code sort} and {@code filters} are opaque to the engine. Only the source knows its columns, so only the
 * source interprets them; the engine merely carries what the spec's sort list and the {@code list-filter} action
 * put there.
 */
public record PageRequest(int page, int size, String sort, Map<String, String> filters) {

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative: " + page);
        }
        if (size <= 0) {
            throw new IllegalArgumentException("page size must be positive: " + size);
        }
        Objects.requireNonNull(sort, "sort");
        filters = Map.copyOf(Objects.requireNonNull(filters, "filters"));
    }

    /** The same request for a different page, keeping the size, sort and filters. */
    public PageRequest atPage(int newPage) {
        return new PageRequest(newPage, size, sort, filters);
    }
}
