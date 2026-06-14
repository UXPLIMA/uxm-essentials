package com.uxplima.uxmessentials.tablist.adapter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import com.uxplima.uxmessentials.shared.display.ConditionParser;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmessentials.tablist.domain.TablistContent;
import com.uxplima.uxmessentials.tablist.domain.TablistFormat;
import com.uxplima.uxmessentials.tablist.domain.TablistFormatConfig;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Parses {@code modules/tablist/config.conf} into a {@link TablistFormatConfig} (the named formats the renderer selects
 * among per viewer) plus the global render cadence the timer re-reads each reschedule. Two operator-authored shapes are
 * accepted, mirroring the scoreboard codec.
 *
 * <p><strong>Multiple formats (current shape).</strong> A {@code formats { <name> { … } }} map, one entry per named
 * format:
 *
 * <pre>{@code
 * refresh-ticks = 20            # GLOBAL: how often every viewer is re-rendered
 * formats {
 *   staff {
 *     condition = "permission:uxmessentials.staff"   # see shared/display/ConditionParser for the grammar
 *     priority = 10                                   # higher wins; ties broken by format name (see below)
 *     header = [ "<red>Staff online" ]
 *     footer = [ "<gray>play.example.net" ]
 *     name-format = "<red>[Staff] {player}"           # how this viewer appears to everyone in the tab list
 *     sort-order = 100                                # higher = shown higher in the tab list; must be positive
 *     world-blacklist = [ "world_the_end" ]
 *   }
 *   default { condition = "", priority = 0, header = [ "<gold>Welcome" ] }
 * }
 * }</pre>
 *
 * <p><strong>Single tablist (back-compat).</strong> When there is no {@code formats { … }} block but a top-level
 * {@code tablist { header, footer, … }} block exists, it is wrapped as one format named {@code default} with an
 * always-true condition, priority {@code 0}, and no name/order override, reproducing the historical single-tablist
 * behaviour. The global refresh cadence then comes from {@code tablist.refresh-ticks}.
 *
 * <p>Every value is operator content rendered through MiniMessage and the placeholder pipeline later, never a
 * {@code MessageKey}. The parse is tolerant: an absent or non-positive {@code refresh-ticks} falls back to one second so
 * the render timer never busy-spins, a non-positive {@code sort-order} is treated as absent (the API requires a positive
 * order), and a format with neither header nor footer nor a name/order override is dropped rather than applying nothing.
 * An empty or virtual root yields {@link Parsed#inert()}.
 *
 * <p><strong>Tie-break.</strong> {@link TablistFormatConfig#select} resolves a viewer to the highest-priority matching
 * format. HOCON does not preserve the order formats are declared in (its object keys iterate alphabetically), so to keep
 * a priority tie deterministic and reload-stable the codec emits the formats sorted by name; on equal priority the
 * alphabetically-first format name wins. Operators give a format a higher {@code priority} to put it first explicitly.
 */
@NullMarked
final class TablistContentCodec {

    private static final long DEFAULT_REFRESH_TICKS = 20L;
    private static final long MILLIS_PER_TICK = 50L;

    private TablistContentCodec() {}

    /**
     * The parsed tablist config: the named formats the renderer selects among, and the global render cadence the timer
     * re-reads each reschedule.
     */
    record Parsed(TablistFormatConfig formats, Duration refreshInterval) {
        Parsed {
            Objects.requireNonNull(formats, "formats");
            Objects.requireNonNull(refreshInterval, "refreshInterval");
        }

        /** The do-nothing default an absent or unreadable config yields: no formats, once a second. */
        static Parsed inert() {
            return new Parsed(TablistFormatConfig.empty(), Duration.ofMillis(DEFAULT_REFRESH_TICKS * MILLIS_PER_TICK));
        }
    }

    /** Parse {@code root}; an empty or virtual root yields {@link Parsed#inert()}. */
    static Parsed read(ConfigurationNode root) {
        if (root.virtual() || root.empty()) {
            return Parsed.inert();
        }
        ConfigurationNode formats = root.node("formats");
        if (!formats.virtual() && formats.isMap()) {
            return new Parsed(readFormats(formats), refreshInterval(root.node("refresh-ticks")));
        }
        return readSingleFormat(root.node("tablist"));
    }

    private static TablistFormatConfig readFormats(ConfigurationNode formats) {
        List<TablistFormat> parsed = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                formats.childrenMap().entrySet()) {
            readFormat(String.valueOf(entry.getKey()), entry.getValue()).ifPresent(parsed::add);
        }
        // HOCON does not preserve declaration order, so sort by name to give TablistFormatConfig.select a
        // deterministic,
        // documented tie-break: on equal priority the alphabetically-first format name wins.
        parsed.sort(Comparator.comparing(TablistFormat::name));
        return new TablistFormatConfig(parsed);
    }

    private static Optional<TablistFormat> readFormat(String name, ConfigurationNode node) {
        if (node.virtual() || !node.isMap()) {
            return Optional.empty();
        }
        TablistContent content = tablistContent(node);
        Optional<String> nameFormat = optionalString(node.node("name-format"));
        OptionalInt sortOrder = sortOrder(node.node("sort-order"));
        // A format that neither shows header/footer nor sets a name or order does nothing; drop it.
        if (content.isBlank() && nameFormat.isEmpty() && sortOrder.isEmpty()) {
            return Optional.empty();
        }
        DisplayCondition condition =
                ConditionParser.parse(node.node("condition").getString());
        int priority = node.node("priority").getInt(0);
        return Optional.of(new TablistFormat(name, condition, priority, content, nameFormat, sortOrder));
    }

    /**
     * Back-compat: wrap a top-level {@code tablist { … }} block as the single implicit {@code default} format with an
     * always-true condition, priority {@code 0}, and no name/order override. A blank block yields no formats. The global
     * refresh cadence comes from {@code tablist.refresh-ticks}.
     */
    private static Parsed readSingleFormat(ConfigurationNode tab) {
        Duration interval = refreshInterval(tab.node("refresh-ticks"));
        TablistContent content = tablistContent(tab);
        if (content.isBlank()) {
            return new Parsed(TablistFormatConfig.empty(), interval);
        }
        TablistFormat single = new TablistFormat(
                "default", DisplayCondition.always(), 0, content, Optional.empty(), OptionalInt.empty());
        return new Parsed(new TablistFormatConfig(List.of(single)), interval);
    }

    private static TablistContent tablistContent(ConfigurationNode node) {
        return new TablistContent(
                strings(node.node("header")),
                strings(node.node("footer")),
                refreshInterval(node.node("refresh-ticks")),
                worldBlacklist(node.node("world-blacklist")));
    }

    private static OptionalInt sortOrder(ConfigurationNode node) {
        if (node.virtual()) {
            return OptionalInt.empty();
        }
        int order = node.getInt(0);
        // The Paper API requires a positive order; treat anything non-positive as "leave the vanilla order untouched".
        return order > 0 ? OptionalInt.of(order) : OptionalInt.empty();
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

    private static Optional<String> optionalString(ConfigurationNode node) {
        String value = node.getString("");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
