package com.uxplima.uxmessentials.scoreboard.adapter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.scoreboard.domain.DisplayContent;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarBoard;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarConfig;
import com.uxplima.uxmessentials.shared.display.ConditionParser;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Parses {@code modules/scoreboard/config.conf} into a {@link SidebarConfig} (the named boards) plus the global render
 * cadence the timer reads each reschedule. Two operator-authored shapes are accepted.
 *
 * <p><strong>Multiple boards (current shape).</strong> A {@code boards { <name> { … } }} map, one entry per named
 * board:
 *
 * <pre>{@code
 * refresh-ticks = 20            # GLOBAL: how often every viewer is re-rendered (per-board intervals are not honoured)
 * boards {
 *   staff {
 *     condition = "permission:uxmessentials.staff"   # see shared/display/ConditionParser for the grammar
 *     priority = 10                                   # higher wins; ties broken by board name (see below)
 *     title = "<red>Staff"
 *     lines = [ "<gray>Mode: <white>staff" ]
 *     hide-score-numbers = true
 *     world-blacklist = [ "world_the_end" ]
 *   }
 *   default { condition = "", priority = 0, title = "<gold>Server", lines = [ "<gray>Welcome" ] }
 * }
 * }</pre>
 *
 * <p><strong>Single board (back-compat).</strong> When there is no {@code boards { … }} block but a top-level
 * {@code scoreboard { title, lines, … }} block exists, it is wrapped as one board named {@code default} with an
 * always-true condition and priority {@code 0}, reproducing the historical single-board-plus-blacklist behaviour. The
 * global refresh cadence then comes from {@code scoreboard.refresh-ticks}.
 *
 * <p>Every value is operator content rendered through MiniMessage and the placeholder pipeline later, never a
 * {@code MessageKey}. The parse is tolerant: a board's lines beyond {@link DisplayContent#MAX_LINES} are truncated, an
 * absent or non-positive {@code refresh-ticks} falls back to one second so the render timer never busy-spins, an absent
 * {@code hide-score-numbers} defaults to {@code true} (the modern look), and a board with neither title nor lines is
 * dropped rather than rendering an empty board. An empty or virtual root yields {@link SidebarConfig#empty()}.
 *
 * <p><strong>Tie-break.</strong> {@link SidebarConfig#select} resolves a viewer to the highest-priority matching board.
 * HOCON does not preserve the order boards are declared in (its object keys iterate alphabetically), so to keep a
 * priority tie deterministic and reload-stable the codec emits the boards sorted by name; on equal priority the
 * alphabetically-first board name wins. Operators give a board a higher {@code priority} to put it first explicitly.
 */
@NullMarked
final class ScoreboardContentCodec {

    private static final long DEFAULT_REFRESH_TICKS = 20L;
    private static final long MILLIS_PER_TICK = 50L;

    private ScoreboardContentCodec() {}

    /**
     * The parsed scoreboard config: the named boards the renderer selects among, and the global render cadence the
     * timer re-reads each reschedule.
     */
    record Parsed(SidebarConfig boards, Duration refreshInterval) {
        Parsed {
            Objects.requireNonNull(boards, "boards");
            Objects.requireNonNull(refreshInterval, "refreshInterval");
        }

        /** The do-nothing default an absent or unreadable config yields: no boards, refreshing once a second. */
        static Parsed inert() {
            return new Parsed(SidebarConfig.empty(), Duration.ofMillis(DEFAULT_REFRESH_TICKS * MILLIS_PER_TICK));
        }
    }

    /** Parse {@code root}; an empty or virtual root yields {@link Parsed#inert()}. */
    static Parsed read(ConfigurationNode root) {
        if (root.virtual() || root.empty()) {
            return Parsed.inert();
        }
        ConfigurationNode boards = root.node("boards");
        if (!boards.virtual() && boards.isMap()) {
            return new Parsed(readBoards(boards), refreshInterval(root.node("refresh-ticks")));
        }
        return readSingleBoard(root.node("scoreboard"));
    }

    private static SidebarConfig readBoards(ConfigurationNode boards) {
        List<SidebarBoard> parsed = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                boards.childrenMap().entrySet()) {
            readBoard(String.valueOf(entry.getKey()), entry.getValue()).ifPresent(parsed::add);
        }
        // HOCON does not preserve the order map keys are declared in, so we cannot rely on declaration order to break a
        // priority tie. Sort by name to give SidebarConfig.select a deterministic, documented tie-break: on equal
        // priority the alphabetically-first board name wins. Priorities are the operator's explicit ordering lever;
        // ties are the edge case this keeps stable across reloads.
        parsed.sort(Comparator.comparing(SidebarBoard::name));
        return new SidebarConfig(parsed);
    }

    private static Optional<SidebarBoard> readBoard(String name, ConfigurationNode node) {
        if (node.virtual() || !node.isMap()) {
            return Optional.empty();
        }
        DisplayContent content = displayContent(node);
        if (content.isBlank()) {
            return Optional.empty();
        }
        DisplayCondition condition =
                ConditionParser.parse(node.node("condition").getString());
        int priority = node.node("priority").getInt(0);
        return Optional.of(new SidebarBoard(name, content, condition, priority));
    }

    /**
     * Back-compat: wrap a top-level {@code scoreboard { … }} block as the single implicit {@code default} board with an
     * always-true condition and priority {@code 0}. A blank block yields no boards.
     */
    private static Parsed readSingleBoard(ConfigurationNode board) {
        Duration interval = refreshInterval(board.node("refresh-ticks"));
        DisplayContent content = displayContent(board);
        if (content.isBlank()) {
            return new Parsed(SidebarConfig.empty(), interval);
        }
        SidebarBoard single = new SidebarBoard("default", content, DisplayCondition.always(), 0);
        return new Parsed(new SidebarConfig(List.of(single)), interval);
    }

    private static DisplayContent displayContent(ConfigurationNode board) {
        return new DisplayContent(
                optionalString(board.node("title")),
                cappedLines(strings(board.node("lines"))),
                board.node("hide-score-numbers").getBoolean(true),
                refreshInterval(board.node("refresh-ticks")),
                worldBlacklist(board.node("world-blacklist")));
    }

    private static Duration refreshInterval(ConfigurationNode node) {
        long ticks = node.getLong(DEFAULT_REFRESH_TICKS);
        if (ticks <= 0L) {
            ticks = DEFAULT_REFRESH_TICKS;
        }
        return Duration.ofMillis(ticks * MILLIS_PER_TICK);
    }

    private static List<String> cappedLines(List<String> lines) {
        if (lines.size() <= DisplayContent.MAX_LINES) {
            return lines;
        }
        return List.copyOf(lines.subList(0, DisplayContent.MAX_LINES));
    }

    private static Set<String> worldBlacklist(ConfigurationNode node) {
        return new LinkedHashSet<>(strings(node));
    }

    private static List<String> strings(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            // A blank entry is kept so an operator can author a spacer line; a missing value is skipped.
            if (value != null) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static Optional<String> optionalString(ConfigurationNode node) {
        String value = node.getString("");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
