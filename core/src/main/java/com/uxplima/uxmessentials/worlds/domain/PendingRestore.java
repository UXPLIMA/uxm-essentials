package com.uxplima.uxmessentials.worlds.domain;

import java.util.Objects;
import java.util.UUID;

/** A staged world restore awaiting operator confirmation: which backup of which world, and by whom. */
public record PendingRestore(WorldName world, BackupId id, UUID requester) {
    public PendingRestore {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requester, "requester");
    }
}
