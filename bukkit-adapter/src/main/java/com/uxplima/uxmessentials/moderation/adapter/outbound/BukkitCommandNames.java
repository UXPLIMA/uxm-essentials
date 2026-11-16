package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;

import com.uxplima.uxmessentials.moderation.application.port.CommandNames;
import org.jspecify.annotations.NullMarked;

/**
 * {@link CommandNames} over the server's command map: one map lookup per word, read on the thread of the player who
 * typed the command, as Paper's own dispatch reads it.
 */
@NullMarked
public final class BukkitCommandNames implements CommandNames {

    private final CommandMap commandMap;

    public BukkitCommandNames(CommandMap commandMap) {
        this.commandMap = Objects.requireNonNull(commandMap, "commandMap");
    }

    @Override
    public Set<String> namesOf(String word) {
        Objects.requireNonNull(word, "word");
        Command command = commandMap.getCommand(word);
        if (command == null) {
            return Set.of();
        }
        Set<String> names = new HashSet<>();
        names.add(bare(command.getName()));
        names.add(bare(command.getLabel()));
        for (String alias : command.getAliases()) {
            names.add(bare(alias));
        }
        return names;
    }

    private static String bare(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':');
        return colon < 0 ? lower : lower.substring(colon + 1);
    }
}
