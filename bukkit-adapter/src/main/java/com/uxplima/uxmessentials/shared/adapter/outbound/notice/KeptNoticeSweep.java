package com.uxplima.uxmessentials.shared.adapter.outbound.notice;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PendingPlayerNotices;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;

/**
 * Drops the kept lines nobody came back for once they are older than {@code notices.keep-days}, so a player who never
 * returns does not hold rows for good. The first sweep runs a few minutes after enable, clear of the start, then
 * hourly, each on the async lane.
 */
public final class KeptNoticeSweep {

    private static final Duration INITIAL_DELAY = Duration.ofMinutes(5);
    private static final Duration PERIOD = Duration.ofHours(1);

    private final PendingPlayerNotices pending;
    private final Duration keepFor;
    private final Scheduler scheduler;
    private final Clock clock;
    private final Logger log;

    public KeptNoticeSweep(
            PendingPlayerNotices pending, Duration keepFor, Scheduler scheduler, Clock clock, Logger log) {
        this.pending = Objects.requireNonNull(pending, "pending");
        this.keepFor = Objects.requireNonNull(keepFor, "keepFor");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Start the repeating sweep, returning the handle that stops it. */
    public AutoCloseable start() {
        return scheduler.repeatGlobal(() -> scheduler.async(this::sweep), INITIAL_DELAY, PERIOD);
    }

    /** One pass: drop every line older than the keep window; a failure is logged and the next pass tries again. */
    void sweep() {
        try {
            int dropped = pending.purgeOlderThan(clock.instant().minus(keepFor));
            if (dropped > 0) {
                log.info("event=player_notices_expired dropped={}", dropped);
            }
        } catch (RuntimeException failure) {
            log.error("failed to drop the expired kept player lines", failure);
        }
    }
}
