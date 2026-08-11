package com.uxplima.uxmessentials.api.view;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One frozen copy of a player's inventory.
 *
 * <p>The items are not here, for the same reason a kit's are not: they are Bukkit item stacks, and a module with
 * no server API on its classpath has no way to carry them honestly. What is here is what a list shows and what a
 * restore needs: which snapshot, whose, why, and when.
 *
 * @param id the snapshot's own id, which is what a restore names
 * @param ownerId the player whose inventory it is
 * @param cause why it was taken
 * @param takenAt when it was taken
 */
public record UxmSnapshot(UUID id, UUID ownerId, UxmSnapshotCause cause, Instant takenAt) {

    public UxmSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(takenAt, "takenAt");
    }
}
