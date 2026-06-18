package com.uxplima.uxmessentials.worlds.domain;

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
}
