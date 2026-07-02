package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The durable RTP pool tuning read from the teleport config: whether the pool is persisted at all, the per-world
 * cap that bounds its growth on disk, how long a validated column survives before the startup sweep discards it, and
 * whether pre-warm skips a world that has no players online. A minimal config yields a working, persisted pool with
 * the documented defaults.
 *
 * @param persist whether validated columns are written to and pre-warmed from the {@code rtp_pool} table
 * @param maxPerWorld the largest number of columns kept on disk per world
 * @param staleAfter how old a column's validation may be before the enable-time sweep removes it
 * @param prewarmRequiresPlayers whether a world with no players online is skipped by the startup pre-warm
 */
@NullMarked
public record RtpPoolSettings(boolean persist, int maxPerWorld, Duration staleAfter, boolean prewarmRequiresPlayers) {

    public RtpPoolSettings {
        Objects.requireNonNull(staleAfter, "staleAfter");
        if (maxPerWorld < 1) {
            throw new IllegalArgumentException("maxPerWorld must be >= 1: " + maxPerWorld);
        }
        if (staleAfter.isNegative() || staleAfter.isZero()) {
            throw new IllegalArgumentException("staleAfter must be positive: " + staleAfter);
        }
    }

    /** Read the pool tuning from {@code config}, applying the documented defaults. */
    public static RtpPoolSettings from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new RtpPoolSettings(
                config.getBoolean("rtp.persist-pool", true),
                Math.max(1, config.getInt("rtp.pool-max-per-world", 200)),
                Duration.ofHours(Math.max(1, config.getInt("rtp.pool-stale-after-hours", 72))),
                config.getBoolean("rtp.pool-prewarm-requires-players", false));
    }
}
