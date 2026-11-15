package com.uxplima.uxmessentials.shared.application.hop;

import java.util.Objects;
import java.util.Optional;

/**
 * What a {@link HopResolver} found for a pending hop on this backend.
 *
 * @param label the name the player knows the destination by, for notices
 * @param landing how to move the player there; empty when the destination is gone or no longer on this backend
 * @param withdrawn true when the hop no longer has a reason to happen (a prisoner released while on the way): it ends
 *     with no move and no failure line, and any charge goes back quietly
 */
public record HopResolution(String label, Optional<HopLanding> landing, boolean withdrawn) {

    public HopResolution {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(landing, "landing");
        if (withdrawn && landing.isPresent()) {
            throw new IllegalArgumentException("a withdrawn hop has no landing");
        }
    }

    public HopResolution(String label, Optional<HopLanding> landing) {
        this(label, landing, false);
    }

    /** A destination that can be reached from here. */
    public static HopResolution landsAt(String label, HopLanding landing) {
        return new HopResolution(label, Optional.of(landing), false);
    }

    /** A destination that is gone, or has moved off this backend. */
    public static HopResolution unreachable(String label) {
        return new HopResolution(label, Optional.empty(), false);
    }

    /** A hop that should no longer happen; nothing went wrong, so nobody is told it failed. */
    public static HopResolution withdrawn(String label) {
        return new HopResolution(label, Optional.empty(), true);
    }
}
