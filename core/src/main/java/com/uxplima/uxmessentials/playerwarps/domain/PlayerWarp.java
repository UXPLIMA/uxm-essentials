package com.uxplima.uxmessentials.playerwarps.domain;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * One player-owned warp: the {@link PlayerRef owner} who created it, a {@link PlayerWarpName} unique within
 * that owner's set, the {@link Position} it points at, a public/private visibility flag, and the moment it
 * was created. A player-warp is a value object: re-anchoring (a move) or flipping its visibility produces a
 * new instance rather than mutating in place.
 *
 * <p>The position carries its own {@link com.uxplima.uxmessentials.shared.domain.WorldRef}, so the warp's
 * world is read from {@code location().world()} rather than held separately. Execution of the teleport to
 * this position is the teleport context's job; this aggregate never moves a player. A new warp is
 * <em>private</em> by default — only its owner may use it until they make it public, after which any player
 * may teleport to it by owner and name.
 *
 * @param owner the player who owns the warp
 * @param name the warp's name, unique within the owner's set
 * @param location where the warp points
 * @param isPublic whether any player may use the warp (true) or only its owner (false)
 * @param createdAt when the warp was first created (preserved across a move or a visibility change)
 */
public record PlayerWarp(PlayerRef owner, PlayerWarpName name, Position location, boolean isPublic, Instant createdAt) {

    public PlayerWarp {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** A new private warp owned by {@code owner}, created now at {@code location}. */
    public static PlayerWarp create(PlayerRef owner, PlayerWarpName name, Position location, Instant createdAt) {
        return new PlayerWarp(owner, name, location, false, createdAt);
    }

    /** A copy re-anchored to {@code newLocation}, keeping the owner, name, visibility, and creation time. */
    public PlayerWarp movedTo(Position newLocation) {
        return new PlayerWarp(owner, name, Objects.requireNonNull(newLocation, "newLocation"), isPublic, createdAt);
    }

    /** A copy with the visibility set to {@code makePublic}, keeping everything else. */
    public PlayerWarp withVisibility(boolean makePublic) {
        return new PlayerWarp(owner, name, location, makePublic, createdAt);
    }
}
