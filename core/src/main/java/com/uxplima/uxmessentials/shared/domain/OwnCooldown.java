package com.uxplima.uxmessentials.shared.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * A destination's own teleport cooldown: the clock it runs on and how long it lasts. Two destinations that each set
 * one must not share a clock, or using the one with the long wait would lock the other for that long too, so the
 * clock is named after the destination itself ({@code warp.spawn}, {@code pwarp.7}) rather than after the verb.
 *
 * <p>The origin of a cross-server hop checks this clock and the target's landing stamps it, so both sides build it
 * from the same destination and reach the same name.
 *
 * @param clock the name of the clock, unique to the destination, e.g. {@code warp.spawn}
 * @param length how long the cooldown lasts, before any tier lowers it
 */
public record OwnCooldown(String clock, Duration length) {

    public OwnCooldown {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(length, "length");
        if (clock.isBlank()) {
            throw new IllegalArgumentException("a cooldown clock must be named");
        }
        if (length.isNegative()) {
            throw new IllegalArgumentException("a cooldown must not be negative: " + length);
        }
    }
}
