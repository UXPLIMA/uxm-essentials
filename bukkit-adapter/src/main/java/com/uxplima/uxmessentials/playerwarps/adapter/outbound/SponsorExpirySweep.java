package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.application.SponsorConfig;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.Sponsorship;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The self-rescheduling sponsor-expiry sweep (the {@code RentSweep} pattern): on a fixed interval, off the tick
 * thread, it retires the sponsorships whose term has lapsed — freeing each warp's slot so another owner can buy it
 * and stamping the post-expiry cooldown ({@code sponsored_until + cooldown-days}) that bars an immediate re-sponsor.
 * Every pass is bounded — the query is capped at {@link #BATCH_LIMIT} rows and indexed on {@code sponsored_until},
 * never a full-table scan — and each warp is retired in isolation, so one warp's fault is logged and the sweep
 * carries on.
 *
 * <p>A disabled sponsor sub-group schedules nothing: {@link #start()} returns immediately when the config is off.
 * Processing one batch per tick keeps a single pass's work bounded; a backlog larger than the batch drains over
 * successive passes rather than in one long off-tick burst.
 */
@NullMarked
public final class SponsorExpirySweep {

    /** Hard cap on the rows any one pass touches — keeps each sweep a bounded index scan, never a full-table read. */
    private static final int BATCH_LIMIT = 500;

    private final PlayerWarpRepository repository;
    private final SponsorConfig config;
    private final Scheduler scheduler;
    private final Duration interval;
    private final Clock clock;
    private final Logger log;
    private volatile boolean running;

    public SponsorExpirySweep(
            PlayerWarpRepository repository,
            SponsorConfig config,
            Scheduler scheduler,
            Duration interval,
            Clock clock,
            Logger log) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.config = Objects.requireNonNull(config, "config");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.interval = Objects.requireNonNull(interval, "interval");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    public void start() {
        if (!config.enabled()) {
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

    /** One bounded pass: free the slot and stamp the cooldown for every warp whose sponsorship term has lapsed. */
    public void sweepOnce() {
        Instant now = clock.instant();
        List<PlayerWarp> expired = repository.expiredSponsorships(now, BATCH_LIMIT);
        int retired = 0;
        for (PlayerWarp warp : expired) {
            try {
                if (retire(warp)) {
                    retired++;
                }
            } catch (RuntimeException failure) {
                log.error("event=playerwarp_sponsor_expiry_failed warp=" + warpId(warp), failure);
            }
        }
        if (retired > 0) {
            log.info("event=playerwarp_sponsor_sweep retired={}", retired);
        }
    }

    /** Retire one warp's lapsed sponsorship, computing its cooldown from the term it was sponsored until. */
    private boolean retire(PlayerWarp warp) {
        Sponsorship sponsorship = warp.sponsorship().orElse(null);
        PlayerWarpId id = warp.id().orElse(null);
        if (sponsorship == null || id == null) {
            return false;
        }
        repository.expireSponsorship(id, sponsorship.activeUntil().plus(config.cooldown()));
        return true;
    }

    private static String warpId(PlayerWarp warp) {
        return warp.id().map(id -> Long.toString(id.value())).orElse("?");
    }
}
