package com.uxplima.uxmessentials.api.bukkit.event.invrollback;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import com.uxplima.uxmessentials.api.view.UxmSnapshotCause;
import org.jspecify.annotations.NullMarked;

/**
 * A stored snapshot was put back onto a player's inventory.
 *
 * <p>Fires after the items are set, so a listener reading the inventory sees the restored one. The player is
 * online by definition: a snapshot is applied to a live inventory and nowhere else.
 *
 * <p>The safety copy taken of what was there before is a capture, not a restore, and fires nothing.
 */
@NullMarked
public final class UxmInventoryRestoreEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID snapshotId;
    private final UxmSnapshotCause cause;
    private final Instant takenAt;

    public UxmInventoryRestoreEvent(
            UUID playerId, String playerName, UUID snapshotId, UxmSnapshotCause cause, Instant takenAt) {
        super(playerId, playerName);
        this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        this.cause = Objects.requireNonNull(cause, "cause");
        this.takenAt = Objects.requireNonNull(takenAt, "takenAt");
    }

    /** The snapshot that was put back. */
    public UUID getSnapshotId() {
        return snapshotId;
    }

    /** Why that snapshot had been taken. */
    public UxmSnapshotCause getCause() {
        return cause;
    }

    /** When it was taken. */
    public Instant getTakenAt() {
        return takenAt;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
