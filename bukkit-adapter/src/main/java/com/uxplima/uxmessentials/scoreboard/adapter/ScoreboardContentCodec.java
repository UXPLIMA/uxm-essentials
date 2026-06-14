package com.uxplima.uxmessentials.scoreboard.adapter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.scoreboard.domain.DisplayContent;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Parses the {@code modules/scoreboard/config.conf} Configurate tree into immutable {@link DisplayContent}. The
 * operator-authored shape is
 *
 * <pre>{@code
 * enabled = false
 * scoreboard {
 *   title = "<gold>My Server"
 *   lines = [ "<gray>Online: <white>%server_online%", "" ]
 *   refresh-ticks = 20            # one tick = 1/20s; 20 = once a second
 *   world-blacklist = [ "world_the_end" ]
 * }
 * }</pre>
 *
 * <p>The tablist header/footer is a separate module now ({@code modules/tablist/config.conf}), so this codec parses
 * only the {@code scoreboard} sidebar block. Every value is operator content rendered through MiniMessage and the
 * placeholder pipeline later, never a {@code MessageKey}. A blank title yields an empty {@link DisplayContent#title()};
 * lines beyond {@link DisplayContent#MAX_LINES} are truncated to the limit so an over-long config degrades rather than
 * failing the load; a non-positive {@code refresh-ticks} falls back to one second so the render timer never
 * busy-spins. An empty or virtual root yields {@link DisplayContent#inert()} — the do-nothing default.
 */
@NullMarked
final class ScoreboardContentCodec {

    private static final long DEFAULT_REFRESH_TICKS = 20L;
    private static final long MILLIS_PER_TICK = 50L;

    private ScoreboardContentCodec() {}

    /** Parse {@code root} into display content; an empty or virtual root yields inert content. */
    static DisplayContent read(ConfigurationNode root) {
        if (root.virtual() || root.empty()) {
            return DisplayContent.inert();
        }
        ConfigurationNode board = root.node("scoreboard");
        return new DisplayContent(
                optionalString(board.node("title")),
                cappedLines(strings(board.node("lines"))),
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
