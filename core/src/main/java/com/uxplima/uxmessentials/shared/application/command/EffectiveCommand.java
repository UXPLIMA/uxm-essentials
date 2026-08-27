package com.uxplima.uxmessentials.shared.application.command;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The resolved registration shape for one command after merging its {@link CommandDefinition} with any
 * {@link CommandOverride}. The adapter registers a node under {@link #name} with {@link #aliases} when
 * {@link #enabled}; a disabled command carries no aliases and is never registered.
 *
 * @param id the stable command id (unchanged by a rename)
 * @param name the effective primary literal to register
 * @param aliases the effective, collision-free alias list
 * @param localizedAliases collision-free extra aliases keyed by normalized BCP-47 locale tag
 * @param enabled whether to register at all
 * @param gui whether running the command bare opens its GUI rather than falling back to its usage text
 */
public record EffectiveCommand(
        CommandId id,
        String name,
        List<String> aliases,
        Map<String, List<String>> localizedAliases,
        boolean enabled,
        boolean gui) {

    /** Compatibility constructor for an effective command with no locale-specific aliases. */
    public EffectiveCommand(CommandId id, String name, List<String> aliases, boolean enabled, boolean gui) {
        this(id, name, aliases, Map.of(), enabled, gui);
    }

    public EffectiveCommand {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(aliases, "aliases");
        Objects.requireNonNull(localizedAliases, "localizedAliases");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("effective name must not be blank");
        }
        aliases = List.copyOf(aliases);
        Map<String, List<String>> snapshot = new LinkedHashMap<>();
        localizedAliases.forEach((locale, values) -> snapshot.put(locale, List.copyOf(values)));
        localizedAliases = Collections.unmodifiableMap(snapshot);
    }

    /** Base and localized aliases deduplicated in stable registration order for Paper's global dispatcher. */
    public List<String> registrationAliases() {
        LinkedHashSet<String> all = new LinkedHashSet<>(aliases);
        localizedAliases.values().forEach(all::addAll);
        return List.copyOf(all);
    }
}
