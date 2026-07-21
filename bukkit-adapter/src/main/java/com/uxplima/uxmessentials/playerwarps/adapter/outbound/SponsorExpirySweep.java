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
 * thread, it retires the sponsorships whose term has lapsed, freeing each warp's slot so another owner can buy it and
 * stamping the post-expiry cooldown ({@code sponsored_until + cooldown-days}) that bars an immediate re-sponsor.
 * Every pass is bounded: the query is capped at {@link #BATCH_LIMIT} rows and indexed on {@code sponsored_until},
 * never a full-table scan, and each warp is retired in isolation, so one warp's fault is logged and the sweep carries
 * on.
 *
 * <p>The scheduling, stop flag and enable gate live in {@link AbstractPeriodicSweep}; this sweep supplies only the
 * per-pass work.
 */
@NullMarked
public final class SponsorExpirySweep extends AbstractPeriodicSweep {

    private final PlayerWarpRepository repository;
    private final SponsorConfig config;

    public SponsorExpirySweep(
            PlayerWarpRepository repository,
            SponsorConfig config,
            Scheduler scheduler,
            Duration interval,
            Clock clock,
            Logger log) {
        super(scheduler, interval, clock, log);
        this.repository = Objects.requireNonNull(repository, "repository");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    protected boolean enabled() {
        return config.enabled();
    }

    /** One bounded pass: free the slot and stamp the cooldown for every warp whose sponsorship term has lapsed. */
    @Override
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
}
