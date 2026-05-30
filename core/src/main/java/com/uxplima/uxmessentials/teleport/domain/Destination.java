package com.uxplima.uxmessentials.teleport.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;

/**
 * Where a teleport will land, resolved to a fixed {@link Position} by the time the flow commits.
 *
 * <p>This is the teleport context's own noun for a landing point — deliberately distinct from the
 * {@code homes} context's {@code Home} and the {@code warps} context's {@code Warp}, which delegate
 * <em>execution</em> here but keep their own aggregates. A destination optionally remembers the
 * follow target it was anchored to (a {@code /tpa}'s requester following the acceptor, a {@code /top}
 * resolving above the mover) so the adapter can re-read a live location at the moment of the hop rather
 * than teleport to a stale snapshot.
 *
 * @param position the resolved landing position
 * @param followTarget the player whose live location supersedes {@link #position} at hop time, if any
 */
public record Destination(Position position, @Nullable PlayerRef followTarget) {

    public Destination {
        Objects.requireNonNull(position, "position");
    }

    /** A fixed-coordinate destination with no live follow target. */
    public static Destination at(Position position) {
        return new Destination(position, null);
    }

    /** A destination that tracks {@code target}'s live location, seeded with their last-known position. */
    public static Destination following(PlayerRef target, Position seed) {
        return new Destination(seed, Objects.requireNonNull(target, "target"));
    }

    /** The follow target whose live location supersedes the seed, when this destination tracks one. */
    public Optional<PlayerRef> follow() {
        return Optional.ofNullable(followTarget);
    }

    /** The uid of this destination's world, the dimension every gating and quota check is scoped to. */
    public UUID worldUid() {
        return position.world().uid();
    }
}
