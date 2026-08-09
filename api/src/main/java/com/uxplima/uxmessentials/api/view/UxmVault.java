package com.uxplima.uxmessentials.api.view;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One of a player's vaults.
 *
 * <p>The contents are deliberately absent, for the same reason a kit's are: they are Bukkit item stacks, and a
 * module with no server API has no way to carry them honestly. What is here is what a list or a selector shows.
 *
 * @param ownerId the player who owns it
 * @param index the vault's number, counting from one, which is what the owner types
 * @param displayName the name the owner gave it, or empty when they never named one
 * @param icon the material id of the icon they chose, or empty for the default
 */
public record UxmVault(UUID ownerId, int index, Optional<String> displayName, Optional<String> icon) {

    public UxmVault {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(icon, "icon");
        if (index < 1) {
            throw new IllegalArgumentException("vault numbers count from one: " + index);
        }
    }

    /** The name if the owner set one, otherwise the number as text, which is what the plugin itself displays. */
    public String label() {
        return displayName.orElseGet(() -> Integer.toString(index));
    }
}
