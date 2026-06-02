package com.uxplima.uxmessentials.playerstate.domain;

/**
 * A health value for {@code /health}, floored at {@code 0} in the domain so the adapter never sets a negative
 * health. Unlike {@code /heal}, which always restores to full, this carries a caller-chosen target: a request
 * of {@code 0} kills the player, and a request beyond the player's maximum is capped to their live maximum.
 *
 * <p>Only the lower bound lives here. Maximum health is a per-player runtime attribute (it varies with
 * scale/attribute modifiers), not a fixed constant like the food bar, so the upper bound is clamped in the
 * adapter against {@code Attribute.MAX_HEALTH} at apply time rather than baked in here.
 *
 * @param value the requested health, floored at {@code 0}
 */
public record HealthLevel(double value) {

    /** A health value floored at {@code 0}; the upper bound is the player's live maximum, capped in the adapter. */
    public static HealthLevel of(double requested) {
        return new HealthLevel(Math.max(0.0, requested));
    }
}
