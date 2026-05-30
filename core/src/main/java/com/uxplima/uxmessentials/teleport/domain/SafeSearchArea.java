package com.uxplima.uxmessentials.teleport.domain;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.WorldRef;

/**
 * The bounded annulus a world's random-teleport candidates are drawn from: a centre, a minimum and
 * maximum radius, and the world-border radius the candidates must stay inside. The policy clamps the
 * effective maximum to the smaller of the configured radius and the border so an RTP never lands a
 * player outside the playable area.
 *
 * @param world the world this area describes
 * @param centerX the x coordinate candidates are measured from (usually the world spawn or 0)
 * @param centerZ the z coordinate candidates are measured from
 * @param minRadius the inner radius candidates must clear (0 for none)
 * @param configuredMaxRadius the operator's per-world outer radius
 * @param borderRadius the world-border radius from the centre, or {@link Double#MAX_VALUE} if unbounded
 */
public record SafeSearchArea(
        WorldRef world,
        double centerX,
        double centerZ,
        double minRadius,
        double configuredMaxRadius,
        double borderRadius) {

    public SafeSearchArea {
        Objects.requireNonNull(world, "world");
        requireFinite(centerX, "centerX");
        requireFinite(centerZ, "centerZ");
        if (minRadius < 0) {
            throw new IllegalArgumentException("minRadius must be >= 0: " + minRadius);
        }
        if (configuredMaxRadius < minRadius) {
            throw new IllegalArgumentException("configuredMaxRadius must be >= minRadius");
        }
        if (borderRadius < 0) {
            throw new IllegalArgumentException("borderRadius must be >= 0: " + borderRadius);
        }
    }

    /** The effective outer radius: the configured radius clamped to the world border. */
    public double maxRadius() {
        return Math.min(configuredMaxRadius, borderRadius);
    }

    /** True when {@code (x, z)} falls within the {@code [minRadius, maxRadius]} annulus from the centre. */
    public boolean contains(double x, double z) {
        double r = radiusOf(x, z);
        return r >= minRadius && r <= maxRadius();
    }

    /** The horizontal distance of {@code (x, z)} from the centre. */
    public double radiusOf(double x, double z) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return Math.sqrt((dx * dx) + (dz * dz));
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite: " + value);
        }
    }
}
