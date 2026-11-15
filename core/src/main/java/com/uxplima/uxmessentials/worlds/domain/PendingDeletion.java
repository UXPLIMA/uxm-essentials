package com.uxplima.uxmessentials.worlds.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A staged world deletion awaiting operator confirmation. */
public record PendingDeletion(WorldName name, UUID requester, Instant stagedAt) {
    public PendingDeletion {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(stagedAt, "stagedAt");
    }

    /**
     * Whether a confirmation arriving at {@code now} comes too late. A request is only a promise about the moment it
     * was made: confirmed hours later, it would delete a world the operator may have stopped thinking about.
     */
    public boolean expiredAt(Instant now, Duration timeout) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(timeout, "timeout");
        // Compared as spans, so a huge configured timeout cannot overflow an Instant.
        return Duration.between(stagedAt, now).compareTo(timeout) > 0;
    }
}
