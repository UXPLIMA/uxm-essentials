package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The per-world random-teleport tuning read from the teleport config: the search radius, the queue's
 * target size and low-water mark, and the per-refill attempt budget. A world with no explicit block
 * inherits the {@code rtp} defaults, so a minimal config still yields a working queue.
 *
 * @param minRadius the inner radius candidates must clear
 * @param maxRadius the operator's outer search radius
 * @param targetSize how many pre-validated locations the queue aims to hold per world
 * @param lowWaterMark the size below which a refill is fired
 * @param attemptBudget the maximum candidate validations one refill cycle runs before yielding
 */
@NullMarked
public record RtpWorldSettings(
        double minRadius, double maxRadius, int targetSize, int lowWaterMark, int attemptBudget) {

    public RtpWorldSettings {
        if (maxRadius < minRadius) {
            throw new IllegalArgumentException("maxRadius must be >= minRadius");
        }
        if (targetSize < 1) {
            throw new IllegalArgumentException("targetSize must be >= 1: " + targetSize);
        }
        if (lowWaterMark < 0 || lowWaterMark > targetSize) {
            throw new IllegalArgumentException("lowWaterMark must be within [0, targetSize]");
        }
        if (attemptBudget < 1) {
            throw new IllegalArgumentException("attemptBudget must be >= 1: " + attemptBudget);
        }
    }

    /** Read the RTP tuning from {@code config}, applying the documented defaults. */
    public static RtpWorldSettings from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        int target = Math.max(1, config.getInt("rtp.queue-target-size", 10));
        int low = Math.min(target, Math.max(0, config.getInt("rtp.queue-low-water", target / 2)));
        return new RtpWorldSettings(
                Math.max(0, config.getInt("rtp.min-radius", 100)),
                Math.max(1, config.getInt("rtp.max-radius", 5000)),
                target,
                low,
                Math.max(1, config.getInt("rtp.attempt-budget", 32)));
    }
}
