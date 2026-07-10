package com.uxplima.uxmessentials.playerwarps.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A paid promotion that pins a warp to a boosted slot until it expires. An owner buys a sponsorship to have their
 * warp float above the ordinary listing; {@link #slot} is the promoted position and {@link #activeUntil} is the
 * instant the promotion lapses. Storing an absolute expiry rather than a duration means the promotion ends at the
 * same wall-clock moment regardless of when it is next read, so a slow read can never silently extend it.
 *
 * @param activeUntil the instant the sponsorship expires
 * @param slot the promoted slot index (zero-based)
 */
public record Sponsorship(Instant activeUntil, int slot) {

    public Sponsorship {
        Objects.requireNonNull(activeUntil, "activeUntil");
        if (slot < 0) {
            throw new IllegalArgumentException("sponsorship slot must not be negative: " + slot);
        }
    }

    /** True while the promotion is still live at {@code now}; it lapses exactly at {@link #activeUntil}. */
    public boolean isActiveAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return now.isBefore(activeUntil);
    }
}
