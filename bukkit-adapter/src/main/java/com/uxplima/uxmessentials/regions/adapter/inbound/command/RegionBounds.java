package com.uxplima.uxmessentials.regions.adapter.inbound.command;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * A resolved cuboid selection: the two opposite corners already sorted into a component-wise minimum and maximum, so
 * a caller hands {@link com.uxplima.uxmessentials.regions.application.port.RegionService#create} a normalised region
 * box regardless of which corner the operator marked first.
 *
 * @param min the lower corner (component-wise minimum of the two selected points)
 * @param max the upper corner (component-wise maximum of the two selected points)
 */
@NullMarked
public record RegionBounds(Position min, Position max) {

    public RegionBounds {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
    }

    /** Sort two corners in the same world into a min/max box; the orientation of either point is dropped. */
    public static RegionBounds of(Position a, Position b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        WorldRef world = a.world();
        Position min = Position.of(world, Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z()));
        Position max = Position.of(world, Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z()));
        return new RegionBounds(min, max);
    }
}
