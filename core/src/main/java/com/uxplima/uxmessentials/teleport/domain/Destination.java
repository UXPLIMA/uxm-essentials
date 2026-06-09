package com.uxplima.uxmessentials.teleport.domain;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;

/**
 * Where a teleport will land, resolved to a fixed {@link Position} by the time the flow commits.
 * Supports optional warmup/cooldown overrides passed by specific triggers (like warps).
 */
public record Destination(
        Position position,
        @Nullable PlayerRef followTarget,
        Optional<Duration> warmupOverride,
        Optional<Duration> cooldownOverride) {

    public Destination {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(warmupOverride, "warmupOverride");
        Objects.requireNonNull(cooldownOverride, "cooldownOverride");
    }

    /** A fixed-coordinate destination with no live follow target. */
    public static Destination at(Position position) {
        return new Destination(position, null, Optional.empty(), Optional.empty());
    }

    /** A fixed-coordinate destination with custom warmup and cooldown overrides. */
    public static Destination at(
            Position position, Optional<Duration> warmupOverride, Optional<Duration> cooldownOverride) {
        return new Destination(position, null, warmupOverride, cooldownOverride);
    }

    /** A destination that tracks {@code target}'s live location, seeded with their last-known position. */
    public static Destination following(PlayerRef target, Position seed) {
        return new Destination(seed, Objects.requireNonNull(target, "target"), Optional.empty(), Optional.empty());
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
