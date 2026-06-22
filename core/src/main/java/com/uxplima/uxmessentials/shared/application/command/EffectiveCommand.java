package com.uxplima.uxmessentials.shared.application.command;

import java.util.List;
import java.util.Objects;

/**
 * The resolved registration shape for one command after merging its {@link CommandDefinition} with any
 * {@link CommandOverride}. The adapter registers a node under {@link #name} with {@link #aliases} when
 * {@link #enabled}; a disabled command carries no aliases and is never registered.
 *
 * @param id the stable command id (unchanged by a rename)
 * @param name the effective primary literal to register
 * @param aliases the effective, collision-free alias list
 * @param enabled whether to register at all
 * @param gui whether running the command bare opens its GUI rather than falling back to its usage text
 */
public record EffectiveCommand(CommandId id, String name, List<String> aliases, boolean enabled, boolean gui) {

    public EffectiveCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(aliases, "aliases");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("effective name must not be blank");
        }
        aliases = List.copyOf(aliases);
    }
}
