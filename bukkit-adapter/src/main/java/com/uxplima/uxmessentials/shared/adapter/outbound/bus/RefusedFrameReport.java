package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Tells the operator about plugin-channel frames refused by the {@link FrameSeal} check, and keeps what the doctor
 * shows: how many were refused and when the last one was. The first refusal is logged at once and later ones at most
 * once a minute, since a client sending forged frames could otherwise flood the log.
 *
 * <p>Frames are checked on the async lane, so the count and both times are atomics, and a compare-and-set lets only one
 * of two racing refusals log.
 */
@NullMarked
final class RefusedFrameReport {

    static final long INTERVAL_MILLIS = 60_000L;

    private static final long NEVER = Long.MIN_VALUE;

    private final Logger log;
    private final LongSupplier millis;
    private final AtomicLong refused = new AtomicLong();
    private final AtomicLong lastRefused = new AtomicLong(NEVER);
    private final AtomicLong lastReport = new AtomicLong(NEVER);

    RefusedFrameReport(Logger log, LongSupplier millis) {
        this.log = Objects.requireNonNull(log, "log");
        this.millis = Objects.requireNonNull(millis, "millis");
    }

    /** A frame was refused for {@code why}. */
    void refused(FrameSeal.Check why) {
        Objects.requireNonNull(why, "why");
        long count = refused.incrementAndGet();
        long now = millis.getAsLong();
        lastRefused.set(now);
        long last = lastReport.get();
        // NEVER is tested on its own: now minus Long.MIN_VALUE overflows to a negative gap that is never due.
        boolean due = last == NEVER || now - last >= INTERVAL_MILLIS;
        if (!due || !lastReport.compareAndSet(last, now)) {
            return;
        }
        if (why == FrameSeal.Check.CLOCK) {
            log.warn(
                    "refused a bus frame on the proxy channel signed more than {}s away from this server's clock ({}"
                            + " refused so far). Keep the clocks of every backend in time; a frame replayed later"
                            + " looks the same",
                    String.valueOf(FrameSeal.CLOCK_TOLERANCE.toSeconds()),
                    String.valueOf(count));
        } else {
            log.warn(
                    "refused a bus frame on the proxy channel that is not signed with this server's network.bus.secret"
                            + " ({} refused so far). Give every backend the same secret and the same release; a"
                            + " player's client sending forged frames looks the same",
                    String.valueOf(count));
        }
    }

    /** How many frames were refused since the transport started. */
    long count() {
        return refused.get();
    }

    /** When the last frame was refused, in epoch milliseconds, empty when none was. */
    OptionalLong last() {
        long last = lastRefused.get();
        return last == NEVER ? OptionalLong.empty() : OptionalLong.of(last);
    }

    /** Forget what was refused, for a transport that stops. */
    void reset() {
        refused.set(0);
        lastRefused.set(NEVER);
        lastReport.set(NEVER);
    }
}
