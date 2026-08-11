package com.uxplima.uxmessentials.invrollback.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A stored snapshot was put back onto a player's live inventory.
 *
 * <p>Published after the items are actually set, so a listener that reads the inventory sees the restored one. The
 * safety copy of what was there before is taken first and is a capture rather than a restore, so it publishes
 * nothing of its own.
 *
 * @param target the player whose inventory was overwritten
 * @param snapshot the snapshot that was put back
 * @param cause why that snapshot had been taken in the first place
 * @param takenAt when it was taken
 */
public record SnapshotRestored(PlayerRef target, SnapshotId snapshot, SnapshotCause cause, Instant takenAt)
        implements InvrollbackEvent {

    public SnapshotRestored {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(takenAt, "takenAt");
    }
}
