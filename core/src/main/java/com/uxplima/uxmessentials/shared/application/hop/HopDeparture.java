package com.uxplima.uxmessentials.shared.application.hop;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * A hop the origin is about to start, described the way the teleport gate and the store need it.
 *
 * @param kind what the hop is heading to
 * @param target the kind-specific reference the target backend resolves again on arrival
 * @param targetServer the network name of the backend the destination lives on
 * @param label the name the player knows the destination by, for notices
 * @param destination where the player will land, as the origin last read it; the gate checks against it
 * @param cooldown the destination's own cooldown, when it overrides the teleport default
 */
public record HopDeparture(
        HopKind kind,
        String target,
        String targetServer,
        String label,
        Position destination,
        Optional<Duration> cooldown) {

    public HopDeparture {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(targetServer, "targetServer");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(cooldown, "cooldown");
    }
}
