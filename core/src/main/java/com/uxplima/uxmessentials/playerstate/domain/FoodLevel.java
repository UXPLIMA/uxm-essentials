package com.uxplima.uxmessentials.playerstate.domain;

/**
 * A food (hunger) level for {@code /foodlevel}, clamped to {@code 0..20} in the domain so the adapter only ever
 * calls {@code Player#setFoodLevel} with a value the server accepts. Unlike {@code /feed}, which always restores
 * to full, this carries a caller-chosen target: a negative request empties the bar, a request beyond the
 * maximum tops it off.
 *
 * <p>The cap is the vanilla maximum food level ({@code 20}), which is fixed by the client's hunger UI rather
 * than a per-player attribute, so it lives here as a constant rather than being passed in by the adapter.
 *
 * @param value the clamped food level, {@code 0..20}
 */
public record FoodLevel(int value) {

    /** The vanilla maximum food level, matching the twenty haunches the client renders. */
    public static final int MAX_FOOD = 20;

    /** A food level clamped to {@code 0..MAX_FOOD}. */
    public static FoodLevel of(int requested) {
        return new FoodLevel(Math.max(0, Math.min(requested, MAX_FOOD)));
    }
}
