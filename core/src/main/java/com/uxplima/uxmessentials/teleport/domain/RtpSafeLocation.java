package com.uxplima.uxmessentials.teleport.domain;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/**
 * A pre-validated random-teleport destination held in a world's pre-warmed queue. It is a {@link
 * Position} that already passed the {@link SafeSearchPolicy} at {@link #validatedAt}, plus the cheap
 * in-memory facts ({@link #radius} from the world centre) needed to revalidate it lazily on serve when
 * the config radius shrank or the world border moved since it was queued.
 *
 * @param position the safe landing position
 * @param radius the horizontal distance from the world's RTP centre, for stale-on-serve checks
 * @param validatedAt when the location last passed the full off-thread validation
 */
public record RtpSafeLocation(Position position, double radius, Instant validatedAt) {

    public RtpSafeLocation {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(validatedAt, "validatedAt");
        if (!Double.isFinite(radius) || radius < 0) {
            throw new IllegalArgumentException("radius must be finite and non-negative: " + radius);
        }
    }

    /** The world this location belongs to — the queue is keyed per world. */
    public WorldRef world() {
        return position.world();
    }

    /**
     * The cheap revalidation a {@code poll()} runs before serving: a location whose radius now exceeds
     * the world's current maximum (the operator shrank the radius, or the border moved inward) is
     * discarded and the next is polled. Biome and chunk-safety are not re-read here — that is the
     * off-thread refill primitive's job; this is the O(1) on-serve guard only.
     */
    public boolean stillWithin(SafeSearchArea area) {
        Objects.requireNonNull(area, "area");
        return radius <= area.maxRadius();
    }
}
