package com.uxplima.uxmessentials.shared.application.command;

import java.util.List;
import java.util.Objects;

/**
 * The code-side default for one command: its stable id plus the name and aliases it registers under
 * when an operator has not overridden them. Contributed by each adapter command at startup and fed,
 * together with the parsed {@link CommandOverride}s, into {@link CommandCatalog#resolve}.
 *
 * @param id the stable command id (the default literal)
 * @param defaultName the literal to register when no override names it
 * @param defaultAliases the aliases to register when no override replaces them
 */
public record CommandDefinition(CommandId id, String defaultName, List<String> defaultAliases) {

    public CommandDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(defaultName, "defaultName");
        Objects.requireNonNull(defaultAliases, "defaultAliases");
        defaultAliases = List.copyOf(defaultAliases);
    }
}
