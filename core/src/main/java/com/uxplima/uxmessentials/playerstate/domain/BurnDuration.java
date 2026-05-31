package com.uxplima.uxmessentials.playerstate.domain;

/**
 * A fire duration for {@code /burn <seconds>}, in seconds, clamped to a sane {@code 0..MAX_SECONDS} range in
 * the domain so the adapter only ever calls {@code Player#setFireTicks} with a non-negative tick count. A
 * request below zero is treated as zero (which puts the player out); a request beyond the cap is held at the
 * cap so a typo cannot set someone alight for an absurd length of time.
 *
 * @param seconds the clamped fire duration in seconds
 */
public record BurnDuration(int seconds) {

    /** Vanilla ticks per second, used to convert to the {@code setFireTicks} tick count. */
    public static final int TICKS_PER_SECOND = 20;

    /** The longest fire {@code /burn} will set, to bound an accidental large argument. */
    public static final int MAX_SECONDS = 3_600;

    public BurnDuration {
        if (seconds < 0) {
            seconds = 0;
        } else if (seconds > MAX_SECONDS) {
            seconds = MAX_SECONDS;
        }
    }

    /** A clamped duration from a raw seconds argument. */
    public static BurnDuration ofSeconds(int seconds) {
        return new BurnDuration(seconds);
    }

    /** The duration as a {@code setFireTicks} tick count. */
    public int ticks() {
        return seconds * TICKS_PER_SECOND;
    }
}
