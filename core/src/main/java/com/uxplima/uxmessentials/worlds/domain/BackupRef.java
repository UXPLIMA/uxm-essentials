package com.uxplima.uxmessentials.worlds.domain;

import java.time.Instant;
import java.util.Objects;

/** A stored world backup: its identity, the moment it was taken, and its archive size on disk. */
public record BackupRef(BackupId id, Instant createdAt, long sizeBytes) {
    public BackupRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative: " + sizeBytes);
        }
    }
}
