package com.uxplima.uxmessentials.homes.domain;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * One named home: an owner, a {@link HomeName}, and the {@link Position} it points at, with the moment
 * it was first created. A home is a value object — moving or renaming one produces a new instance rather
 * than mutating in place, so the {@link HomeSet} aggregate stays immutable between operations.
 *
 * <p>The position carries its own {@link com.uxplima.uxmessentials.shared.domain.WorldRef}, so the home's
 * world (which the limit quota may be scoped to) is read from {@code location().world()} rather than held
 * separately. Execution of the teleport to this position is the teleport context's job; this aggregate
 * never moves a player.
 *
 * @param owner the player who owns the home
 * @param name the home's canonical name within the owner's set
 * @param location where the home points
 * @param createdAt when the home was first created (preserved across a move or rename)
 */
public record Home(PlayerRef owner, HomeName name, Position location, Instant createdAt) {

    public Home {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** A new home created now at {@code location}. */
    public static Home create(PlayerRef owner, HomeName name, Position location, Instant createdAt) {
        return new Home(owner, name, location, createdAt);
    }

    /** A copy re-anchored to {@code newLocation}, keeping the name, owner, and original creation time. */
    public Home movedTo(Position newLocation) {
        return new Home(owner, name, Objects.requireNonNull(newLocation, "newLocation"), createdAt);
    }

    /** A copy under {@code newName}, keeping the location, owner, and original creation time. */
    public Home renamedTo(HomeName newName) {
        return new Home(owner, Objects.requireNonNull(newName, "newName"), location, createdAt);
    }
}
