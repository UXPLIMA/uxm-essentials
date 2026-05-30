package com.uxplima.uxmessentials.warps.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * One server-wide warp: a {@link WarpName}, the {@link Position} it points at, the owner who created it,
 * the moment it was created, and two optional gates — a {@link WarpCost} charged through the economy
 * context when one is present, and a {@code requiredPermission} node a player must hold to use it beyond the
 * implicit {@code uxmessentials.warp.use.<warp>} node. A warp is a value object: re-anchoring (a move)
 * produces a new instance rather than mutating in place.
 *
 * <p>The position carries its own {@link com.uxplima.uxmessentials.shared.domain.WorldRef}, so the warp's
 * world is read from {@code location().world()} rather than held separately. Execution of the teleport to
 * this position is the teleport context's job; this aggregate never moves a player. Both gates are
 * optional and data-driven — a warp opts into a cost or an extra permission at creation; the common warp
 * is free and ungated.
 *
 * @param name the warp's canonical, server-unique name
 * @param location where the warp points
 * @param owner the player who created the warp (attribution, shown by {@code /warpinfo})
 * @param createdAt when the warp was first created (preserved across a move)
 * @param cost the price to use the warp; {@link WarpCost#free()} when there is no charge
 * @param requiredPermission an extra permission node required to use the warp, if the warp sets one
 */
public record Warp(
        WarpName name,
        Position location,
        PlayerRef owner,
        Instant createdAt,
        WarpCost cost,
        Optional<String> requiredPermission) {

    public Warp {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(requiredPermission, "requiredPermission");
    }

    /** A new free, ungated warp created now at {@code location}. */
    public static Warp create(WarpName name, Position location, PlayerRef owner, Instant createdAt) {
        return new Warp(name, location, owner, createdAt, WarpCost.free(), Optional.empty());
    }

    /**
     * A new warp created now with an optional cost and an optional extra permission gate. A blank
     * permission is treated as "no extra gate" so an absent config value never produces an unsatisfiable
     * node.
     */
    public static Warp create(
            WarpName name,
            Position location,
            PlayerRef owner,
            Instant createdAt,
            WarpCost cost,
            Optional<String> requiredPermission) {
        return new Warp(name, location, owner, createdAt, cost, normalisePermission(requiredPermission));
    }

    /** A copy re-anchored to {@code newLocation}, keeping the name, owner, gates, and original creation time. */
    public Warp movedTo(Position newLocation) {
        return new Warp(
                name, Objects.requireNonNull(newLocation, "newLocation"), owner, createdAt, cost, requiredPermission);
    }

    /** True when the warp sets a cost the economy gate should charge (a non-free price). */
    public boolean hasCost() {
        return !cost.isFree();
    }

    private static Optional<String> normalisePermission(Optional<String> permission) {
        Objects.requireNonNull(permission, "requiredPermission");
        return permission.map(String::strip).filter(node -> !node.isEmpty());
    }
}
