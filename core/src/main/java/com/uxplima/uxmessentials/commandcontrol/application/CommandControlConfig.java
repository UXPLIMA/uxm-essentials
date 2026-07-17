package com.uxplima.uxmessentials.commandcontrol.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.commandcontrol.domain.RuleMode;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The typed, immutable view of {@code modules/commandcontrol/config.conf}: the enable gate, the whitelist/blacklist
 * mode, which deny message to show, and the {@code default} plus per-group command lists. It is resolved once from
 * the module's scoped {@link ConfigStore} when the module starts and, per the atomic-reload rule, rebuilt whole on
 * reload — so a command dispatched mid-reload sees one coherent snapshot.
 *
 * <p>The per-group lists live under {@code commands { default = […], <group> = […] }}: the {@code default} key is the
 * fallback list applied to a player whose group has no list, and every other key names a permission group. Reading
 * the group names off the config tree ({@link ConfigStore#getKeys}) keeps the set open — an operator adds a group by
 * adding a key, with no code change.
 *
 * @param enabled the module enable gate ({@code enabled}, default {@code true})
 * @param mode whether the lists are a whitelist or a blacklist ({@code mode}, default {@code blacklist})
 * @param useUnknownCommandMessage show the vanilla-style "unknown command" line on deny rather than the
 *     "no permission" line ({@code use-unknown-command-message}, default {@code true})
 * @param defaultCommands the fallback command list ({@code commands.default})
 * @param groupCommands the per-group command lists, keyed by permission group name
 */
public record CommandControlConfig(
        boolean enabled,
        RuleMode mode,
        boolean useUnknownCommandMessage,
        List<String> defaultCommands,
        Map<String, List<String>> groupCommands) {

    /** The config key under {@code commands} that holds the fallback list rather than a named group. */
    private static final String DEFAULT_LIST_KEY = "default";

    public CommandControlConfig {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(defaultCommands, "defaultCommands");
        Objects.requireNonNull(groupCommands, "groupCommands");
        defaultCommands = List.copyOf(defaultCommands);
        Map<String, List<String>> copied = new LinkedHashMap<>();
        groupCommands.forEach((group, list) -> copied.put(group, List.copyOf(list)));
        groupCommands = Map.copyOf(copied);
    }

    /** Resolve the command-control config from the module's scoped {@link ConfigStore} ({@code modules.commandcontrol}). */
    public static CommandControlConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        List<String> defaults = config.getStringList("commands." + DEFAULT_LIST_KEY, List.of());
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String key : config.getKeys("commands")) {
            if (!key.equalsIgnoreCase(DEFAULT_LIST_KEY)) {
                groups.put(key, config.getStringList("commands." + key, List.of()));
            }
        }
        return new CommandControlConfig(
                config.getBoolean("enabled", true),
                RuleMode.fromConfig(config.getString("mode", "blacklist"), RuleMode.BLACKLIST),
                config.getBoolean("use-unknown-command-message", true),
                defaults,
                groups);
    }

    /** Build the pure {@link RuleSet} this config describes, gating {@code .bypass} on {@code bypassPermission}. */
    public RuleSet toRuleSet(String bypassPermission) {
        return RuleSet.of(mode, defaultCommands, groupCommands, bypassPermission);
    }

    /** The deny line to show on a blocked command, per {@link #useUnknownCommandMessage}. */
    public CommandControlMessageKey denyMessage() {
        return useUnknownCommandMessage
                ? CommandControlMessageKey.COMMANDCONTROL_UNKNOWN_COMMAND
                : CommandControlMessageKey.COMMANDCONTROL_NO_PERMISSION;
    }
}
