package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.network.BusChannel;
import org.jspecify.annotations.NullMarked;

/**
 * Tells the operator about bus frames dropped for being over the plugin-messaging limit. The first drop is reported
 * at once, later ones at most once a minute, so a frame type that keeps producing them cannot flood the log.
 *
 * <p>Frames are sent from several threads, so the drop count and the time of the last report are atomics, and a
 * compare-and-set lets only one of two racing drops report.
 */
@NullMarked
final class OversizeFrameReport {

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
    OversizeFrameReport(Logger log, LongSupplier millis) {
        this.log = Objects.requireNonNull(log, "log");
        this.millis = Objects.requireNonNull(millis, "millis");
    }

    /** A frame of {@code bytes} bytes was dropped. */
    void dropped(int bytes) {
        long count = dropped.incrementAndGet();
        long now = millis.getAsLong();
        long last = lastReport.get();
        // NEVER is tested on its own: now minus Long.MIN_VALUE overflows to a negative gap that is never due.
        boolean due = last == NEVER || now - last >= INTERVAL_MILLIS;
        if (due && lastReport.compareAndSet(last, now)) {
            log.warn(
                    "dropped a bus frame of {} bytes, over the {} byte plugin-messaging limit ({} dropped so far)",
                    String.valueOf(bytes),
                    String.valueOf(BusChannel.MAX_FRAME_BYTES),
                    String.valueOf(count));
        }
    }
}
