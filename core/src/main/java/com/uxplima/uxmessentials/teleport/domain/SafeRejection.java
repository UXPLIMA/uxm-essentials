package com.uxplima.uxmessentials.teleport.domain;

/**
 * Why the {@link SafeSearchPolicy} rejected a random-teleport candidate. Surfaced to the refill loop's
 * diagnostics (a world that keeps rejecting on {@link #INSIDE_CLAIM} is claim-saturated and will not
 * fill — that is logged, not spun on) rather than to a player.
 */
public enum SafeRejection {

    /** The candidate fell outside the world's radius annulus or past the world border. */
    OUT_OF_BOUNDS,

    /** The biome is on the operator's excluded list (ocean, deep ocean, …). */
    EXCLUDED_BIOME,

    /** The resolved column has no safe footing or breathable space. */
    UNSAFE_GROUND,

    /** The candidate falls inside a protected claim. */
    INSIDE_CLAIM,

    /** The block the player would land on is on the operator's avoid list (lava, magma, …). */
    AVOIDED_BLOCK,

    /** The resolved landing Y falls outside the operator's configured min/max band. */
    OUT_OF_Y_BAND
}
