package com.uxplima.uxmessentials.scoreboard.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The operator-authored content of the per-player scoreboard sidebar: the sidebar title and lines, the refresh
 * cadence the render timer reads each cycle, and the set of world names where the sidebar is suppressed. Every string
 * is raw MiniMessage source the adapter renders per viewer through the placeholder pipeline; the domain never parses
 * or localises them — it only guards the structural invariants.
 *
 * <p>A sidebar shows at most {@link #MAX_LINES} lines (the vanilla scoreboard limit uxmLib's {@code Sidebar}
 * enforces), so {@code lines} over that bound is rejected at construction rather than silently truncated downstream.
 * The refresh interval must be strictly positive — a zero or negative cadence would busy-spin the render timer.
 *
 * <p>{@code hideScoreNumbers} hides the red per-line score numbers vanilla draws down the right edge of the sidebar
 * (the adapter applies a blank number format to the objective). It defaults on — the clean, modern look operators
 * expect — and is purely a render concern, not a structural one, so it never affects {@link #isBlank()}.
 *
 * @param title the sidebar title source, empty when the operator left it blank (the sidebar then has no heading)
 * @param lines the sidebar line sources, top to bottom, at most {@link #MAX_LINES}
 * @param hideScoreNumbers whether to suppress the red right-edge score numbers; on for the modern look
 * @param refreshInterval how often the render timer re-renders every viewer; strictly positive
 * @param worldBlacklist world names where the sidebar is suppressed entirely
 */
public record DisplayContent(
        Optional<String> title,
        List<String> lines,
        boolean hideScoreNumbers,
        Duration refreshInterval,
        Set<String> worldBlacklist) {

    /** The maximum number of sidebar lines a vanilla scoreboard can show; mirrors uxmLib {@code Sidebar.MAX_LINES}. */
    public static final int MAX_LINES = 15;

    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(1L);

    public DisplayContent {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(refreshInterval, "refreshInterval");
        Objects.requireNonNull(worldBlacklist, "worldBlacklist");
        if (lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("a sidebar shows at most " + MAX_LINES + " lines, got " + lines.size());
        }
        if (refreshInterval.isZero() || refreshInterval.isNegative()) {
            throw new IllegalArgumentException("refresh interval must be positive, got " + refreshInterval);
        }
        lines = List.copyOf(lines);
        worldBlacklist = Set.copyOf(worldBlacklist);
    }

    /**
     * The do-nothing default a freshly enabled, unauthored module renders: no title, no sidebar lines, suppressed
     * nowhere, refreshing once a second. An operator sees no visible change until they author content.
     */
    public static DisplayContent inert() {
        return new DisplayContent(Optional.empty(), List.of(), true, DEFAULT_INTERVAL, Set.of());
    }

    /** True when {@code worldName} is on the blacklist and the sidebar must be suppressed there. */
    public boolean suppressedIn(String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        return worldBlacklist.contains(worldName);
    }

    /** True when nothing is configured to show — no title and no lines. */
    public boolean isBlank() {
        return title.isEmpty() && lines.isEmpty();
    }
}
