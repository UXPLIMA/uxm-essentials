package com.uxplima.uxmessentials.tablist.adapter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.uxplima.uxmessentials.tablist.domain.TablistContent;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Parses the {@code modules/tablist/config.conf} Configurate tree into immutable {@link TablistContent}. The
 * operator-authored shape is
 *
 * <pre>{@code
 * enabled = false
 * tablist {
 *   header = [ "<gold>Welcome" ]
 *   footer = [ "<gray>play.example.net" ]
 *   refresh-ticks = 20            # one tick = 1/20s; 20 = once a second
 *   world-blacklist = [ "world_the_end" ]
 * }
 * }</pre>
 *
 * <p>Every value is operator content rendered through MiniMessage and the placeholder pipeline later, never a
 * {@code MessageKey}. The header and footer are line lists the adapter joins with newlines, so they carry no count
 * bound; a non-positive {@code refresh-ticks} falls back to one second so the render timer never busy-spins. An empty
 * or virtual root yields {@link TablistContent#inert()} — the do-nothing default.
 */
@NullMarked
final class TablistContentCodec {

    private static final long DEFAULT_REFRESH_TICKS = 20L;
    private static final long MILLIS_PER_TICK = 50L;

    private TablistContentCodec() {}

    /** Parse {@code root} into tablist content; an empty or virtual root yields inert content. */
    static TablistContent read(ConfigurationNode root) {
        if (root.virtual() || root.empty()) {
            return TablistContent.inert();
        }
        ConfigurationNode tab = root.node("tablist");
        return new TablistContent(
                strings(tab.node("header")),
                strings(tab.node("footer")),
                refreshInterval(tab.node("refresh-ticks")),
                worldBlacklist(tab.node("world-blacklist")));
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
}
