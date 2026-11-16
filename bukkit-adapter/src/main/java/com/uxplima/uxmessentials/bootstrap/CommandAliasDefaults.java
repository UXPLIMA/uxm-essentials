package com.uxplima.uxmessentials.bootstrap;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.shared.application.command.CommandDefinition;
import com.uxplima.uxmessentials.shared.application.command.CommandSpec;
import com.uxplima.uxmessentials.shared.application.command.CommandSpecCatalog;

/**
 * Folds the aliases {@link CommandSpecCatalog} publishes into each command's code-side defaults before the catalog
 * resolves the operator's overrides.
 *
 * <p>The short forms people reach for from other plugins ({@code /m}, {@code /tell}, {@code /v}, {@code /gm},
 * {@code /h}, {@code /eat}, {@code /godmode}, {@code /lore}) answer out of the box without anyone editing
 * {@code commands.conf}, and the command reference lists exactly the aliases that are registered, because both read
 * the same entry. The catalogue's own test keeps every alias clear of every other command's name, so nothing folded
 * in here can steal a word a real command registers under.
 */
public final class CommandAliasDefaults {

    private CommandAliasDefaults() {}

    /**
     * Returns a copy of {@code defs} where every command the catalogue lists carries its published aliases. The code
     * defaults come first and keep their order; the catalogue's follow, deduplicated. A command the catalogue does not
     * list, such as an operator's custom command, is returned unchanged.
     */
    public static List<CommandDefinition> augment(List<CommandDefinition> defs) {
        Objects.requireNonNull(defs, "defs");
        List<CommandDefinition> out = new ArrayList<>(defs.size());
        for (CommandDefinition def : defs) {
            Optional<CommandSpec> spec = CommandSpecCatalog.find(def.id().value());
            out.add(spec.isEmpty() ? def : new CommandDefinition(def.id(), def.defaultName(), merge(def, spec.get())));
        }
        return List.copyOf(out);
    }

    private static List<String> merge(CommandDefinition def, CommandSpec spec) {
        Set<String> merged = new LinkedHashSet<>(def.defaultAliases());
        merged.addAll(spec.aliases());
        return List.copyOf(merged);
    }
}
