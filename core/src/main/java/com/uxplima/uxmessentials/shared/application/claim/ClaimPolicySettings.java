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
 * @param ownerOnly when {@code true}, placement inside a claim requires the player to <em>own</em>
 *     the claim rather than merely be trusted in it; the {@code playerwarps} "may only warp on land
 *     you own" mode maps onto this knob. Defaults to {@code false}, which keeps the owner-or-member
 *     behaviour every other consumer relies on.
 */
@NullMarked
public record ClaimPolicySettings(
        boolean requireClaim,
        boolean blockForeignClaims,
        int foreignChunkDistance,
        boolean checkTeleportAccess,
        boolean ownerOnly) {

    public ClaimPolicySettings {
        if (foreignChunkDistance < 0) {
            throw new IllegalArgumentException("foreignChunkDistance must be >= 0, got " + foreignChunkDistance);
        }
    }

    /**
     * Delegating constructor for the consumers that predate the {@code ownerOnly} knob (poses,
     * homes, teleport). It fixes {@code ownerOnly} to {@code false} so their behaviour is unchanged.
     */
    public ClaimPolicySettings(
            boolean requireClaim, boolean blockForeignClaims, int foreignChunkDistance, boolean checkTeleportAccess) {
        this(requireClaim, blockForeignClaims, foreignChunkDistance, checkTeleportAccess, false);
    }

    /** Permissive defaults: nothing is blocked, proximity checks are off, membership stays owner-or-member. */
    public static ClaimPolicySettings defaults() {
        return new ClaimPolicySettings(false, false, 0, false, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClaimPolicySettings that)) return false;
        return requireClaim == that.requireClaim
                && blockForeignClaims == that.blockForeignClaims
                && foreignChunkDistance == that.foreignChunkDistance
                && checkTeleportAccess == that.checkTeleportAccess
                && ownerOnly == that.ownerOnly;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requireClaim, blockForeignClaims, foreignChunkDistance, checkTeleportAccess, ownerOnly);
    }
}
