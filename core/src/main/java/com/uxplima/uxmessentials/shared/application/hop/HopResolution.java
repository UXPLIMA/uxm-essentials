package com.uxplima.uxmessentials.shared.application.hop;

import java.util.Objects;
import java.util.Optional;

/**
 * What a {@link HopResolver} found for a pending hop on this backend.
 *
 * @param label the name the player knows the destination by, for notices
 * @param landing how to move the player there; empty when the destination is gone or no longer on this backend
 */
public record HopResolution(String label, Optional<HopLanding> landing) {

    public HopResolution {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(landing, "landing");
    }

    /** A destination that can be reached from here. */
    public static HopResolution landsAt(String label, HopLanding landing) {
        return new HopResolution(label, Optional.of(landing));
    }

    /** A destination that is gone, or has moved off this backend. */
    public static HopResolution unreachable(String label) {
        return new HopResolution(label, Optional.empty());
    }
}
