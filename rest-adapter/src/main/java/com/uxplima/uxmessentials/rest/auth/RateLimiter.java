package com.uxplima.uxmessentials.rest.auth;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * How many requests one token may make in a minute.
 *
 * <p>A fixed window rather than a sliding one, because an operator has to be able to picture the limit they are
 * setting: "a hundred and twenty a minute" is a sentence, and a token that spends its whole allowance in the first
 * second waits out the rest of that minute.
 *
 * <p>Counted per label, so one panel misbehaving does not lock out another.
 */
public final class RateLimiter {

    private final int perMinute;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int perMinute, Clock clock) {
        if (perMinute < 1) {
            throw new IllegalArgumentException("the limit must be at least one a minute: " + perMinute);
        }
        this.perMinute = perMinute;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Count one request against {@code label}, answering whether it is within the limit. */
    public boolean allow(String label) {
        long minute = clock.millis() / 60_000L;
        Window window = windows.compute(
                Objects.requireNonNull(label, "label"),
                (name, current) -> current == null || current.minute != minute ? new Window(minute) : current);
        return window.used.incrementAndGet() <= perMinute;
    }

    /** How many seconds until this token's allowance resets, for the {@code Retry-After} header. */
    public long secondsUntilReset() {
        return 60L - (clock.millis() / 1000L) % 60L;
    }

    /** One minute's worth of counting for one token. */
    private static final class Window {

        private final long minute;
        private final AtomicInteger used = new AtomicInteger();

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
