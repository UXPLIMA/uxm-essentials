package com.uxplima.uxmessentials.security.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * The players allowed to be server operators ({@code allowed-operators}). While the list is on, an operator who is not
 * on it loses operator and is sent away, and nobody can be made an operator who is not on it. An entry is a player
 * name, matched without regard to case, or a UUID; a UUID entry keeps a player listed after a rename.
 *
 * <p>While the list is off it permits everybody, so the default of an empty, switched-off list changes nothing.
 */
public final class OperatorList {

    private static final OperatorList OFF = new OperatorList(false, Set.of(), Set.of());

    private final boolean enabled;
    private final Set<String> names;
    private final Set<UUID> ids;

    private OperatorList(boolean enabled, Set<String> names, Set<UUID> ids) {
        this.enabled = enabled;
        this.names = names;
        this.ids = ids;
    }

    /** The list {@code entries} describe, in force only when {@code enabled}. A blank entry is skipped. */
    public static OperatorList of(boolean enabled, List<String> entries) {
        Objects.requireNonNull(entries, "entries");
        if (!enabled) {
            return OFF;
        }
        Set<String> names = new HashSet<>();
        Set<UUID> ids = new HashSet<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String trimmed = entry.strip();
            UUID id = uuid(trimmed);
            if (id != null) {
                ids.add(id);
            } else {
                names.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        return new OperatorList(true, Set.copyOf(names), Set.copyOf(ids));
    }

    /** Whether the list is in force. */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Whether the player known by {@code id} and {@code name} may be an operator: always while the list is off,
     * otherwise when either names a listed entry. Either may be unknown, as for a player who never joined.
     */
    public boolean permits(@Nullable UUID id, @Nullable String name) {
        if (!enabled) {
            return true;
        }
        return (id != null && ids.contains(id)) || (name != null && names.contains(name.toLowerCase(Locale.ROOT)));
    }

    private static @Nullable UUID uuid(String text) {
        if (text.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException notUuid) {
            return null;
        }
    }
}
