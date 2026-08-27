package com.uxplima.uxmessentials.shared.application.command;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One command's operator override, parsed from a root {@code commands.conf} entry. Absent fields
 * fall back to the {@link CommandDefinition}: an empty {@link #name} keeps the default literal, and the
 * {@link #aliases} list replaces the defaults wholesale when present.
 *
 * @param enabled whether the command is registered at all
 * @param name the replacement primary literal, or empty to keep the default
 * @param aliases the replacement alias list
 * @param gui whether running the command bare opens its GUI, or empty to inherit the global default
 * @param localizedAliases extra aliases keyed by BCP-47 locale tag; they never replace the canonical surface
 */
public record CommandOverride(
        boolean enabled,
        Optional<String> name,
        List<String> aliases,
        Optional<Boolean> gui,
        Map<String, List<String>> localizedAliases) {

    /** Compatibility constructor for a command with no locale-specific aliases. */
    public CommandOverride(boolean enabled, Optional<String> name, List<String> aliases, Optional<Boolean> gui) {
        this(enabled, name, aliases, gui, Map.of());
    }

    public CommandOverride {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(aliases, "aliases");
        Objects.requireNonNull(gui, "gui");
        Objects.requireNonNull(localizedAliases, "localizedAliases");
        aliases = List.copyOf(aliases);
        Map<String, List<String>> snapshot = new LinkedHashMap<>();
        localizedAliases.forEach((locale, values) -> snapshot.put(locale, List.copyOf(values)));
        localizedAliases = Collections.unmodifiableMap(snapshot);
    }
}
