package com.uxplima.uxmessentials.scoreboard.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The operator-authored content of the per-player scoreboard display: the sidebar title and lines, the tablist
 * header and footer, the refresh cadence the render timer reads each cycle, and the set of world names where the
 * display is suppressed. Every string is raw MiniMessage source the adapter renders per viewer through the
 * placeholder pipeline; the domain never parses or localises them — it only guards the structural invariants.
 *
 * <p>A sidebar shows at most {@link #MAX_LINES} lines (the vanilla scoreboard limit uxmLib's {@code Sidebar}
 * enforces), so {@code lines} over that bound is rejected at construction rather than silently truncated downstream.
 * The refresh interval must be strictly positive — a zero or negative cadence would busy-spin the render timer. The
 * tablist header/footer are line lists joined with newlines by the adapter, so they carry no count bound of their own.
 *
 * @param title the sidebar title source, empty when the operator left it blank (the display then has no heading)
 * @param lines the sidebar line sources, top to bottom, at most {@link #MAX_LINES}
 * @param header the tablist header line sources, joined with newlines by the adapter
 * @param footer the tablist footer line sources, joined with newlines by the adapter
 * @param refreshInterval how often the render timer re-renders every viewer; strictly positive
 * @param worldBlacklist world names where the display is suppressed entirely
 */
public record DisplayContent(
        Optional<String> title,
        List<String> lines,
        List<String> header,
        List<String> footer,
        Duration refreshInterval,
        Set<String> worldBlacklist) {

    /** The maximum number of sidebar lines a vanilla scoreboard can show; mirrors uxmLib {@code Sidebar.MAX_LINES}. */
    public static final int MAX_LINES = 15;

    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(1L);

    public DisplayContent {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(footer, "footer");
        Objects.requireNonNull(refreshInterval, "refreshInterval");
        Objects.requireNonNull(worldBlacklist, "worldBlacklist");
        if (lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("a sidebar shows at most " + MAX_LINES + " lines, got " + lines.size());
        }
        if (refreshInterval.isZero() || refreshInterval.isNegative()) {
            throw new IllegalArgumentException("refresh interval must be positive, got " + refreshInterval);
        }
        lines = List.copyOf(lines);
        header = List.copyOf(header);
        footer = List.copyOf(footer);
        worldBlacklist = Set.copyOf(worldBlacklist);
    }

    /**
     * The do-nothing default a freshly enabled, unauthored module renders: no title, no sidebar lines, no tablist
     * header or footer, suppressed nowhere, refreshing once a second. An operator sees no visible change until they
     * author content.
     */
    public static DisplayContent inert() {
        return new DisplayContent(Optional.empty(), List.of(), List.of(), List.of(), DEFAULT_INTERVAL, Set.of());
    }

    /** True when {@code worldName} is on the blacklist and the display must be suppressed there. */
    public boolean suppressedIn(String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        return worldBlacklist.contains(worldName);
    }

    /** True when nothing is configured to show — no title, no lines, no header, no footer. */
    public boolean isBlank() {
        return title.isEmpty() && lines.isEmpty() && header.isEmpty() && footer.isEmpty();
    }
}
