package com.uxplima.uxmessentials.worlds.domain;

import java.util.Objects;

/**
 * A resolved portal exit: the world to arrive in and the exact coordinates within it. Carries the
 * stable {@link WorldName} identity rather than a Bukkit world so the value survives the world being
 * unloaded; the adapter resolves it to a live location at the boundary. Coordinates are validated
 * finite so a corrupt portal link can never carry a {@code NaN} or infinite destination into a
 * teleport.
 *
 * @param world the world to arrive in
 * @param x destination x coordinate
 * @param y destination y coordinate
 * @param z destination z coordinate
 */
public record PortalDestination(WorldName world, double x, double y, double z) {

    public PortalDestination {
        Objects.requireNonNull(world, "world");
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite: " + value);
        }
    }
}
