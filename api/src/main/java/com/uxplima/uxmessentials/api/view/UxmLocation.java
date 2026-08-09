package com.uxplima.uxmessentials.api.view;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * A point in a world, named by the world's name rather than a Bukkit handle.
 *
 * <p>The name rather than the {@code World} because an event can describe a place in a world that is not loaded, and
 * because this type also travels through the pure API where Bukkit is not on the classpath. Turn it into a Bukkit
 * location with {@code Bukkit.getWorld(loc.world())}, which answers {@code null} for a world that is unloaded.
 */
@NullMarked
public record UxmLocation(String world, double x, double y, double z, float yaw, float pitch) {

    public UxmLocation {
        Objects.requireNonNull(world, "world");
    }

    /** A location with no facing, for the many places that only care about the point. */
    public UxmLocation(String world, double x, double y, double z) {
        this(world, x, y, z, 0f, 0f);
    }
}
