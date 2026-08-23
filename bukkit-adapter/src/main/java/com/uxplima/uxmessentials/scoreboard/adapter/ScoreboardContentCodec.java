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
import com.uxplima.uxmessentials.scoreboard.domain.SidebarLine;
import com.uxplima.uxmessentials.scoreboard.domain.SidebarNumberFormat;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationDef;
import com.uxplima.uxmessentials.shared.application.port.Logger;
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
     * The parsed scoreboard config: the named boards the renderer selects among, the named animations the renderer
     * expands {@code %anim_<name>%} tokens against, and the global render cadence the timer re-reads each reschedule.
     */
    record Parsed(SidebarConfig boards, List<AnimationDef> animations, Duration refreshInterval) {
        Parsed {
            Objects.requireNonNull(boards, "boards");
            animations = List.copyOf(Objects.requireNonNull(animations, "animations"));
            Objects.requireNonNull(refreshInterval, "refreshInterval");
        }

        /** The do-nothing default an absent or unreadable config yields: no boards, no animations, once a second. */
        static Parsed inert() {
            return new Parsed(
                    SidebarConfig.empty(), List.of(), Duration.ofMillis(DEFAULT_REFRESH_TICKS * MILLIS_PER_TICK));
        }
    }

    /** Parse {@code root}; an empty or virtual root yields {@link Parsed#inert()}. {@code log} reports skipped entries. */
    static Parsed read(ConfigurationNode root, Logger log) {
        Objects.requireNonNull(log, "log");
        if (root.virtual() || root.empty()) {
            return Parsed.inert();
        }
        List<AnimationDef> animations = AnimationDef.parseAll(root.node("animations"), log);
        ConfigurationNode boards = root.node("boards");
        if (!boards.virtual() && boards.isMap()) {
            return new Parsed(readBoards(boards, log), animations, refreshInterval(root.node("refresh-ticks")));
        }
        Parsed single = readSingleBoard(root.node("scoreboard"), log);
        return new Parsed(single.boards(), animations, single.refreshInterval());
    }

    private static SidebarConfig readBoards(ConfigurationNode boards, Logger log) {
        List<SidebarBoard> parsed = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                boards.childrenMap().entrySet()) {
            readBoard(String.valueOf(entry.getKey()), entry.getValue(), log).ifPresent(parsed::add);
        }
        // HOCON does not preserve the order map keys are declared in, so we cannot rely on declaration order to break a
        // priority tie. Sort by name to give SidebarConfig.select a deterministic, documented tie-break: on equal
        // priority the alphabetically-first board name wins. Priorities are the operator's explicit ordering lever;
        // ties are the edge case this keeps stable across reloads.
        parsed.sort(Comparator.comparing(SidebarBoard::name));
        return new SidebarConfig(parsed);
    }

    private static Optional<SidebarBoard> readBoard(String name, ConfigurationNode node, Logger log) {
        if (node.virtual() || !node.isMap()) {
            return Optional.empty();
        }
        DisplayContent content = displayContent(node, log);
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
     * always-true condition and priority {@code 0}. A blank block yields no boards. Animations are read separately by
     * {@link #read} and merged in there, so the returned {@code animations} list is always empty.
     */
    private static Parsed readSingleBoard(ConfigurationNode board, Logger log) {
        Duration interval = refreshInterval(board.node("refresh-ticks"));
        DisplayContent content = displayContent(board, log);
        if (content.isBlank()) {
            return new Parsed(SidebarConfig.empty(), List.of(), interval);
        }
        SidebarBoard single = new SidebarBoard("default", content, DisplayCondition.always(), 0);
        return new Parsed(new SidebarConfig(List.of(single)), List.of(), interval);
    }

    private static DisplayContent displayContent(ConfigurationNode board, Logger log) {
        boolean hideScoreNumbers = board.node("hide-score-numbers").getBoolean(true);
        return DisplayContent.typed(
                optionalString(board.node("title")),
                lines(board.node("lines"), hideScoreNumbers, log),
                hideScoreNumbers,
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

    private static List<SidebarLine> lines(ConfigurationNode node, boolean hideScoreNumbers, Logger log) {
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        List<SidebarLine> values = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        int sourceIndex = 0;
        for (ConfigurationNode child : node.childrenList()) {
            sourceIndex++;
            Optional<SidebarLine> parsed = child.isMap()
                    ? typedLine(child, sourceIndex, hideScoreNumbers)
                    : legacyLine(child.getString(), sourceIndex, hideScoreNumbers);
            if (parsed.isEmpty()) {
                log.warn("scoreboard_line_skipped index={} reason=missing_or_invalid", sourceIndex);
                continue;
            }
            SidebarLine line = parsed.orElseThrow();
            if (!ids.add(line.id())) {
                log.warn("scoreboard_line_skipped index={} id={} reason=duplicate_id", sourceIndex, line.id());
                continue;
            }
            if (values.size() == DisplayContent.MAX_CANDIDATE_LINES) {
                log.warn("scoreboard_lines_truncated limit={}", DisplayContent.MAX_CANDIDATE_LINES);
                break;
            }
            values.add(line);
        }
        return List.copyOf(values);
    }

    private static Optional<SidebarLine> legacyLine(String source, int sourceIndex, boolean hideScoreNumbers) {
        if (source == null) {
            return Optional.empty();
        }
        String conditionSource = "";
        String textAndRight = source;
        int conditionAt = source.indexOf(" | ");
        if (conditionAt >= 0) {
            conditionSource = source.substring(0, conditionAt);
            textAndRight = source.substring(conditionAt + 3);
        }
        int rightAt = textAndRight.indexOf("||");
        String text = rightAt < 0 ? textAndRight : textAndRight.substring(0, rightAt);
        SidebarNumberFormat format = rightAt < 0
                ? defaultNumberFormat(hideScoreNumbers)
                : SidebarNumberFormat.fixed(textAndRight.substring(rightAt + 2));
        return Optional.of(
                new SidebarLine("line-" + sourceIndex, text, ConditionParser.parse(conditionSource), format, false));
    }

    private static Optional<SidebarLine> typedLine(ConfigurationNode node, int sourceIndex, boolean hideScoreNumbers) {
        String text = node.node("text").getString();
        if (text == null) {
            return Optional.empty();
        }
        String id = node.node("id").getString("line-" + sourceIndex);
        try {
            return Optional.of(new SidebarLine(
                    id,
                    text,
                    ConditionParser.parse(node.node("condition").getString()),
                    numberFormat(node.node("number-format"), hideScoreNumbers),
                    node.node("hide-when-empty").getBoolean(false)));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private static SidebarNumberFormat numberFormat(ConfigurationNode node, boolean hideScoreNumbers) {
        if (node.virtual()) {
            return defaultNumberFormat(hideScoreNumbers);
        }
        if (node.isMap()) {
            String type = node.node("type").getString("fixed");
            if (type.equalsIgnoreCase("fixed")) {
                return SidebarNumberFormat.fixed(node.node("text").getString(""));
            }
            return namedNumberFormat(type, hideScoreNumbers);
        }
        return namedNumberFormat(node.getString(""), hideScoreNumbers);
    }

    private static SidebarNumberFormat namedNumberFormat(String value, boolean hideScoreNumbers) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "default", "score" -> SidebarNumberFormat.defaultFormat();
            case "blank", "hidden", "hide" -> SidebarNumberFormat.blank();
            default -> defaultNumberFormat(hideScoreNumbers);
        };
    }

    private static SidebarNumberFormat defaultNumberFormat(boolean hideScoreNumbers) {
        return hideScoreNumbers ? SidebarNumberFormat.blank() : SidebarNumberFormat.defaultFormat();
    }

    private static Optional<String> optionalString(ConfigurationNode node) {
        String value = node.getString("");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
