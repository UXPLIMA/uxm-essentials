package com.uxplima.uxmessentials.bossbar.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The time a bar is up: an event with a start and an end, or an announcement that runs for a number of seconds. The
 * bar counts its end down, and a bar drawn by time drains from full at the start to empty at the end.
 *
 * @param start when the bar comes up; empty for a bar that is up from now until its end
 * @param end when the bar comes down
 */
public record BossBarWindow(Optional<Instant> start, Instant end) {

    public BossBarWindow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (start.isPresent() && !end.isAfter(start.get())) {
            throw new IllegalArgumentException("a boss bar window must end after it starts");
        }
    }

    /** Whether the bar is up at {@code now}: from its start, and until its end. */
    public boolean activeAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return start.map(begin -> !now.isBefore(begin)).orElse(true) && now.isBefore(end);
    }

    /** The time left at {@code now}; zero once the window has ended. */
    public Duration remaining(Instant now) {
        Objects.requireNonNull(now, "now");
        Duration left = Duration.between(now, end);
        return left.isNegative() ? Duration.ZERO : left;
    }

    /** How much of the window is left at {@code now}, from 1 at the start to 0 at the end; 1 with no start. */
    public double fractionLeft(Instant now) {
        Objects.requireNonNull(now, "now");
        if (start.isEmpty()) {
            return now.isBefore(end) ? 1.0 : 0.0;
        }
        long total = Duration.between(start.get(), end).toMillis();
        double left = (double) remaining(now).toMillis() / total;
        return Math.max(0.0, Math.min(1.0, left));
    }
}
