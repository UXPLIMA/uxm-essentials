package com.uxplima.uxmessentials.shared.application.command;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One command's operator override, parsed from a {@code commands/<module>.conf} entry. Absent fields
 * fall back to the {@link CommandDefinition}: an empty {@link #name} keeps the default literal, and the
 * {@link #aliases} list replaces the defaults wholesale when present.
 *
 * @param enabled whether the command is registered at all
 * @param name the replacement primary literal, or empty to keep the default
 * @param aliases the replacement alias list
 */
public record CommandOverride(boolean enabled, Optional<String> name, List<String> aliases) {

    public CommandOverride {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(aliases, "aliases");
        aliases = List.copyOf(aliases);
    }
}
