package com.uxplima.uxmessentials.playerstate.domain;

/**
 * A remaining-air value for {@code /air}, in ticks, clamped to {@code 0..maximum} in the domain so the adapter
 * only ever calls {@code Player#setRemainingAir} with a value the server accepts. Operators think in seconds;
 * {@link #ofSeconds(int, int)} converts at the vanilla twenty ticks per second so {@code /air 15} reads as
 * fifteen seconds of breath.
 *
 * <p>The maximum is the player's own {@code getMaximumAir} (vanilla {@code 300} ticks), passed in by the
 * adapter, so the clamp tracks any attribute or modifier rather than a hard-coded cap. A negative request
 * empties the lungs; a request beyond the maximum tops them off.
 *
 * @param ticks the clamped remaining air in ticks
 */
public record AirAmount(int ticks) {

    /** Vanilla ticks per second, used to convert an operator-facing seconds argument. */
    public static final int TICKS_PER_SECOND = 20;

    /** A remaining-air value in ticks, clamped to {@code 0..maximumTicks}. */
    public static AirAmount ofTicks(int requested, int maximumTicks) {
        int cap = Math.max(0, maximumTicks);
        int clamped = Math.max(0, Math.min(requested, cap));
        return new AirAmount(clamped);
    }

    /** A remaining-air value given in seconds, converted to ticks and clamped to {@code 0..maximumTicks}. */
    public static AirAmount ofSeconds(int seconds, int maximumTicks) {
        long requested = (long) seconds * TICKS_PER_SECOND;
        int bounded = (int) Math.max(Integer.MIN_VALUE, Math.min(requested, Integer.MAX_VALUE));
        return ofTicks(bounded, maximumTicks);
    }

    /** The value rounded down to whole seconds, for the confirmation message. */
    public int seconds() {
        return ticks / TICKS_PER_SECOND;
    }
}
