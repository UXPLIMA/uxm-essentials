package com.uxplima.uxmessentials.bootstrap.health;

import java.util.List;
import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import org.jspecify.annotations.NullMarked;

/**
 * Names, for {@code /uxmess doctor}, any installed plugin that owns commands we also own. Two plugins both
 * registering {@code /home} is legal and Bukkit resolves it by load order, which is exactly why it is worth
 * saying out loud: the usual symptom is "my {@code /sethome} silently does nothing", and the usual cause is that
 * another plugin won the name.
 *
 * <p>This is always a warning, never an error. Running both is a legitimate setup: an operator migrating away
 * from Essentials keeps it for one feature and disables our overlapping modules, and a network may want CMI's
 * moderation with our homes. The check states the overlap and leaves the decision alone.
 *
 * <p><b>These are not integrations.</b> A conflicting plugin is deliberately absent from
 * {@code IntegrationCatalog} and from {@code paper-plugin.yml}: we do not call into any of them, and declaring a
 * {@code load: BEFORE} dependency on Essentials would ask the server to load our competitor first so it could
 * take the command names, which is precisely the outcome this check exists to report.
 */
@NullMarked
public final class CommandConflictHealthCheck implements HealthCheck {

    private static final List<Conflict> CONFLICTS = List.of(
            new Conflict("Essentials", "homes, warps, economy and most player commands"),
            new Conflict("CMI", "homes, warps, economy, moderation and most player commands"),
            new Conflict("Multiverse-Core", "world management commands"));

    private final PluginManager plugins;

    public CommandConflictHealthCheck(PluginManager plugins) {
        this.plugins = Objects.requireNonNull(plugins, "plugins");
    }

    @Override
    public String name() {
        return "command-conflicts";
    }

    @Override
    public HealthResult check() {
        List<String> found = CONFLICTS.stream()
                .filter(conflict -> installed(conflict.plugin()))
                .map(conflict -> conflict.plugin() + " (" + conflict.surface() + ")")
                .toList();
        if (found.isEmpty()) {
            return HealthResult.ok("no plugin with overlapping commands installed");
        }
        return HealthResult.warn("commands may be shadowed by " + String.join(", ", found)
                + "; whichever plugin registers a name first wins, so disable the overlapping module on one side");
    }

    /** True when the plugin is installed and enabled: one that failed its own startup registered nothing. */
    private boolean installed(String pluginName) {
        Plugin plugin = plugins.getPlugin(pluginName);
        return plugin != null && plugin.isEnabled();
    }

    /** A plugin we do not integrate with, paired with the command surface it overlaps. */
    private record Conflict(String plugin, String surface) {

        Conflict {
            Objects.requireNonNull(plugin, "plugin");
            Objects.requireNonNull(surface, "surface");
        }
    }
}
