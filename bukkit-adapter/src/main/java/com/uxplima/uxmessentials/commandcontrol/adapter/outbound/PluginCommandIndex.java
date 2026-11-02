package com.uxplima.uxmessentials.commandcontrol.adapter.outbound;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginIdentifiableCommand;

import org.jspecify.annotations.NullMarked;

/**
 * The commands one plugin has registered, read off the server's command map for a {@code plugin:<name>} list entry.
 * Every label the map knows that the plugin owns is returned, bare and namespaced, so an alias registered by the
 * plugin is covered and a bare label another plugin took over is not. The map is read on the server's main thread.
 */
@NullMarked
public final class PluginCommandIndex {

    private final CommandMap commandMap;

    public PluginCommandIndex(CommandMap commandMap) {
        this.commandMap = Objects.requireNonNull(commandMap, "commandMap");
    }

    /** The labels {@code plugin}, matched without regard to case, owns right now; empty when it owns none. */
    public Collection<String> commandsOf(String plugin) {
        Objects.requireNonNull(plugin, "plugin");
        Set<String> labels = new LinkedHashSet<>();
        for (Map.Entry<String, Command> known : commandMap.getKnownCommands().entrySet()) {
            if (known.getValue() instanceof PluginIdentifiableCommand owned
                    && owned.getPlugin().getName().equalsIgnoreCase(plugin)) {
                labels.add(known.getKey().toLowerCase(Locale.ROOT));
            }
        }
        return labels;
    }
}
