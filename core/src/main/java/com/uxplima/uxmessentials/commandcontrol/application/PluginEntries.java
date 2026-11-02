package com.uxplima.uxmessentials.commandcontrol.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Opens the {@code plugin:<name>} entries of a command list into every command that plugin has registered, its aliases
 * and namespaced forms included, so one line covers a whole plugin: {@code "plugin:uxmCosmetics"} lists
 * {@code /cosmetics}, {@code /kozmetik}, {@code /uxmcosmetics:cosmetics} and the rest. {@code "!plugin:<name>"} takes
 * them all back out. A plugin that is not installed, or has no commands, opens into nothing.
 */
public final class PluginEntries {

    private static final String PREFIX = "plugin:";

    private PluginEntries() {}

    /**
     * {@code entries} with each {@code plugin:<name>} entry replaced by the commands {@code commandsOf} gives for that
     * plugin name, each carrying the entry's {@code !} when it had one. Every other entry is kept as written.
     */
    public static List<String> expand(List<String> entries, Function<String, Collection<String>> commandsOf) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(commandsOf, "commandsOf");
        List<String> out = new ArrayList<>(entries.size());
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.strip();
            boolean exception = trimmed.startsWith("!");
            String body = exception ? trimmed.substring(1).strip() : trimmed;
            if (!body.toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
                out.add(entry);
                continue;
            }
            String plugin = body.substring(PREFIX.length()).strip();
            if (plugin.isEmpty()) {
                continue;
            }
            // Each label goes in behind a slash, which the list reading takes off again, so a label can never be
            // read as the ! of an exception.
            for (String command : commandsOf.apply(plugin)) {
                out.add((exception ? "!/" : "/") + command);
            }
        }
        return List.copyOf(out);
    }
}
