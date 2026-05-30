package com.uxplima.uxmessentials.teleport.domain;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * The off-thread-read facts about one random-teleport candidate, handed to {@link SafeSearchPolicy} for
 * a pure accept/reject verdict. The adapter loads the destination chunk on the candidate's region
 * thread, reads its biome and the safety of the resolved Y, and fills this record; the domain decides
 * nothing about chunk loading, only whether the facts pass the policy.
 *
 * @param position the candidate landing position (with a safe-Y already resolved by the adapter)
 * @param biome the biome at the candidate, for the excluded-biome check
 * @param standingSafe whether the resolved column has solid ground and breathable space (adapter-read)
 * @param insideClaim whether the candidate falls inside a protected claim (adapter-read, false if no
 *     claim plugin is present)
 */
public record SafeCandidate(Position position, BiomeName biome, boolean standingSafe, boolean insideClaim) {

    public SafeCandidate {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(biome, "biome");
    }

    /** The candidate's x coordinate, for the radius/border check. */
    public double x() {
        return position.x();
    }

    /** The candidate's z coordinate, for the radius/border check. */
    public double z() {
        return position.z();
    }
}
