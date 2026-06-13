package com.uxplima.uxmessentials.shared.application.claim;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * Operator-configured claim-policy knobs. Loaded once on enable and swapped atomically on reload.
 *
 * @param requireClaim when {@code true}, a home may only be placed inside a claim the player
 *     trusts; placing in unclaimed land is denied
 * @param blockForeignClaims when {@code true}, a home may not be placed inside a claim that belongs
 *     to another player even if the player has no own claims nearby
 * @param foreignChunkDistance the chunk radius (≥ 1) around the target block to check for foreign
 *     claims; 0 means the proximity check is disabled
 * @param checkTeleportAccess when {@code true}, teleporting to a home inside a claim requires the
 *     player to still be trusted there; denied otherwise
 */
@NullMarked
public record ClaimPolicySettings(
        boolean requireClaim, boolean blockForeignClaims, int foreignChunkDistance, boolean checkTeleportAccess) {

    public ClaimPolicySettings {
        if (foreignChunkDistance < 0) {
            throw new IllegalArgumentException("foreignChunkDistance must be >= 0, got " + foreignChunkDistance);
        }
    }

    /** Permissive defaults: nothing is blocked and proximity checks are off. */
    public static ClaimPolicySettings defaults() {
        return new ClaimPolicySettings(false, false, 0, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClaimPolicySettings that)) return false;
        return requireClaim == that.requireClaim
                && blockForeignClaims == that.blockForeignClaims
                && foreignChunkDistance == that.foreignChunkDistance
                && checkTeleportAccess == that.checkTeleportAccess;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requireClaim, blockForeignClaims, foreignChunkDistance, checkTeleportAccess);
    }
}
