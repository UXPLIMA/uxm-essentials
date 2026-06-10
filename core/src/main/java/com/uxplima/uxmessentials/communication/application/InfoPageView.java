package com.uxplima.uxmessentials.communication.application;

import java.util.List;
import java.util.Objects;

/**
 * One rendered slice of an {@link com.uxplima.uxmessentials.communication.domain.InfoPage}: the body lines on the
 * requested page plus the page coordinates the adapter needs to draw the header/footer chrome. The {@link #lines}
 * are still raw operator MiniMessage content (never a {@code MessageKey}); only the {@link #page}/{@link #pageCount}
 * coordinates flow into the plugin's own paginated header and footer strings.
 *
 * <p>{@link #page} is clamped to {@code 1..pageCount} by the {@link ShowInfoPage} use case, so a viewer who asks for
 * a page past the end is served the last page rather than an empty slice.
 *
 * @param command the info page's command literal, echoed into the header/footer
 * @param lines the body lines on this page, in author order
 * @param page the 1-based page number actually served (clamped into range)
 * @param pageCount the total number of pages the body spans, at least one
 */
public record InfoPageView(String command, List<String> lines, int page, int pageCount) {

    public InfoPageView {
        Objects.requireNonNull(command, "command");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1: " + page);
        }
        if (pageCount < 1) {
            throw new IllegalArgumentException("pageCount must be >= 1: " + pageCount);
        }
        if (page > pageCount) {
            throw new IllegalArgumentException("page " + page + " exceeds pageCount " + pageCount);
        }
    }

    /** Whether the page spans more than one screen, so the adapter draws the page header and footer. */
    public boolean isPaginated() {
        return pageCount > 1;
    }
}
