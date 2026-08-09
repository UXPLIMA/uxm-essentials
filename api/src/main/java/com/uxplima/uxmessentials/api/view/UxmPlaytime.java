package com.uxplima.uxmessentials.api.view;

import java.time.Duration;
import java.util.Objects;

/**
 * How long a player has been on the server, split into time spent playing and time spent away.
 *
 * <p>The windows are the ones {@code /playtime} shows, each summed over the per-day ledger, so today's figure
 * resets at the server's midnight rather than a rolling twenty-four hours. A player nobody has sampled yet reads
 * as zero everywhere rather than as no answer.
 *
 * @param todayActive time at the keyboard on the server's current day
 * @param todayAfk time away on the server's current day
 * @param weekActive time at the keyboard over the last seven days, today included
 * @param weekAfk time away over the last seven days, today included
 * @param monthActive time at the keyboard over the last thirty days, today included
 * @param monthAfk time away over the last thirty days, today included
 * @param totalActive time at the keyboard over every recorded day
 * @param totalAfk time away over every recorded day
 */
public record UxmPlaytime(
        Duration todayActive,
        Duration todayAfk,
        Duration weekActive,
        Duration weekAfk,
        Duration monthActive,
        Duration monthAfk,
        Duration totalActive,
        Duration totalAfk) {

    public UxmPlaytime {
        Objects.requireNonNull(todayActive, "todayActive");
        Objects.requireNonNull(todayAfk, "todayAfk");
        Objects.requireNonNull(weekActive, "weekActive");
        Objects.requireNonNull(weekAfk, "weekAfk");
        Objects.requireNonNull(monthActive, "monthActive");
        Objects.requireNonNull(monthAfk, "monthAfk");
        Objects.requireNonNull(totalActive, "totalActive");
        Objects.requireNonNull(totalAfk, "totalAfk");
    }

    /** Everything at zero, which is what a player who has never been sampled reads as. */
    public static UxmPlaytime empty() {
        Duration zero = Duration.ZERO;
        return new UxmPlaytime(zero, zero, zero, zero, zero, zero, zero, zero);
    }

    /** Time connected over every recorded day, away time included, which is what most consumers mean by playtime. */
    public Duration totalConnected() {
        return totalActive.plus(totalAfk);
    }
}
