package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Shared scaffolding for the playerwarps background sweeps (rent and sponsor expiry): a self-rescheduling task that,
 * on a fixed interval and off the tick thread, runs one bounded pass and then queues the next. A disabled sub-group
 * schedules nothing: {@link #start()} returns immediately when {@link #enabled()} is false. Processing one batch per
 * pass keeps a single pass's work bounded; a backlog larger than the batch drains over successive passes rather than
 * in one long off-tick burst.
 *
 * <p>Each concrete sweep supplies its own enable check ({@link #enabled()}) and its own per-pass work
 * ({@link #sweepOnce()}); the interval, scheduling and stop flag live here.
 */
@NullMarked
abstract class AbstractPeriodicSweep {

    /** Hard cap on the rows any one pass touches: keeps each sweep a bounded index scan, never a full-table read. */
    protected static final int BATCH_LIMIT = 500;

    private final Scheduler scheduler;
    private final Duration interval;
    protected final Clock clock;
    protected final Logger log;
    private volatile boolean running;

    protected AbstractPeriodicSweep(Scheduler scheduler, Duration interval, Clock clock, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.interval = Objects.requireNonNull(interval, "interval");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    public void start() {
        if (!enabled()) {
            return;
        }
        running = true;
        scheduleNext();
    }

    public void stop() {
        running = false;
    }

    private void scheduleNext() {
        if (running) {
            scheduler.asyncAfter(interval, this::tick);
        }
    }

    private void tick() {
        if (!running) {
            return;
        }
        scheduler.async(() -> {
            sweepOnce();
            scheduleNext();
        });
    }

    /** Whether this sweep's sub-group is enabled; a disabled sweep schedules and touches nothing. */
    protected abstract boolean enabled();

    /** One bounded pass of the sweep's own work. */
    public abstract void sweepOnce();

    /** The warp's id as a stable log token, or {@code ?} for an unsaved warp, for per-warp fault logging. */
    protected static String warpId(PlayerWarp warp) {
        return warp.id().map(id -> Long.toString(id.value())).orElse("?");
    }
}
