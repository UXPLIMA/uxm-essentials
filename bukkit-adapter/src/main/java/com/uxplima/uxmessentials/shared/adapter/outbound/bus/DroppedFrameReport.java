package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Tells the operator about bus frames dropped because the plugin-messaging queue was full. The first drop is reported
 * at once, later ones at most once a minute with the running count, so a backend that stays empty cannot flood the
 * log and no drop goes unnoticed.
 *
 * <p>Frames are queued from several threads, so the count and the time of the last report are atomics, and a
 * compare-and-set lets only one of two racing drops report.
 */
@NullMarked
final class DroppedFrameReport {

    static final long INTERVAL_MILLIS = 60_000L;

    private static final long NEVER = Long.MIN_VALUE;

    private final Logger log;
    private final LongSupplier millis;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong lastReport = new AtomicLong(NEVER);

    /**
     * @param log where the report goes
     * @param millis the current time in milliseconds
     */
    DroppedFrameReport(Logger log, LongSupplier millis) {
        this.log = Objects.requireNonNull(log, "log");
        this.millis = Objects.requireNonNull(millis, "millis");
    }

    /** A frame of {@code type} was dropped from a full queue of {@code capacity} frames. */
    void dropped(String type, int capacity) {
        long count = dropped.incrementAndGet();
        long now = millis.getAsLong();
        long last = lastReport.get();
        // NEVER is tested on its own: now minus Long.MIN_VALUE overflows to a negative gap that is never due.
        boolean due = last == NEVER || now - last >= INTERVAL_MILLIS;
        if (due && lastReport.compareAndSet(last, now)) {
            log.warn(
                    "dropped a {} bus frame: the plugin-messaging queue of {} frames is full while it waits for a"
                            + " player to carry it ({} dropped so far); raise network.bus.outbound-queue-size, or use"
                            + " transport = redis, which has no such queue",
                    type,
                    String.valueOf(capacity),
                    String.valueOf(count));
        }
    }
}
