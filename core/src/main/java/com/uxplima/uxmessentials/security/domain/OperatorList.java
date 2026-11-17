package com.uxplima.uxmessentials.security.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.NameCase;
import org.jspecify.annotations.Nullable;

/**
 * The players allowed to be server operators ({@code allowed-operators}). While the list is on, an operator who is not
 * on it loses operator and is sent away, and nobody can be made an operator who is not on it. An entry is a player
 * name or a UUID; a UUID entry keeps a player listed after a rename.
 *
 * <p>How a name entry matches follows the server's {@link NameCase}. With authenticated logins a name has one owner,
 * so it matches in any case. On an offline-mode server every spelling logs in as its own account, so {@code Admin}
 * permits {@code Admin} only: matching {@code aDMIN} as well would hand operator to whoever joins under that spelling.
 *
 * <p>While the list is off it permits everybody, so the default of an empty, switched-off list changes nothing.
 */
public final class OperatorList {

    private static final OperatorList OFF = new OperatorList(false, Set.of(), Set.of());

    private final boolean enabled;
    private final Set<String> names;
    private final Set<String> lowerNames;
    private final Set<UUID> ids;

    private OperatorList(boolean enabled, Set<String> names, Set<UUID> ids) {
        this.enabled = enabled;
        this.names = names;
        Set<String> lower = new HashSet<>();
        for (String name : names) {
            lower.add(name.toLowerCase(Locale.ROOT));
        }
        this.lowerNames = Set.copyOf(lower);
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
                names.add(trimmed);
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
     * otherwise when either names a listed entry, the name compared as {@code nameCase} says. Either may be unknown,
     * as for a player who never joined.
     */
    public boolean permits(@Nullable UUID id, @Nullable String name, NameCase nameCase) {
        Objects.requireNonNull(nameCase, "nameCase");
        if (!enabled) {
            return true;
        }
        if (id != null && ids.contains(id)) {
            return true;
        }
        if (name == null) {
            return false;
        }
        return nameCase == NameCase.SENSITIVE
                ? names.contains(name)
                : lowerNames.contains(name.toLowerCase(Locale.ROOT));
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
