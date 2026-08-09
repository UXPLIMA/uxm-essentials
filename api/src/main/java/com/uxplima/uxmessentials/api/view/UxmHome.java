package com.uxplima.uxmessentials.api.view;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One of a player's homes.
 *
 * @param ownerId the player who owns it
 * @param slot the slot it occupies, counting from zero, which is also its database key
 * @param location where it points
 * @param label the name the owner gave it, or empty when they never named one
 * @param icon the material id of the icon they chose, or empty for the default
 * @param isPublic whether other players may see and use it
 * @param createdAt when it was first set
 * @param updatedAt when it was last changed
 */
public record UxmHome(
        UUID ownerId,
        int slot,
        UxmLocation location,
        Optional<String> label,
        Optional<String> icon,
        boolean isPublic,
        Instant createdAt,
        Instant updatedAt) {

    public UxmHome {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (slot < 0) {
            throw new IllegalArgumentException("home slot must not be negative: " + slot);
        }
    }

    /** The number the owner sees in chat and in the grid, counting from one. */
    public int slotNumber() {
        return slot + 1;
    }

    /** The label if there is one, otherwise the slot number as text, which is what the plugin itself displays. */
    public String displayName() {
        return label.orElseGet(() -> Integer.toString(slotNumber()));
    }
}
