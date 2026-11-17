package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmRestoreDelivery;

/**
 * Putting a stored inventory back.
 *
 * <p>The same act {@code /invrestore} performs, with the same safety copy: what the player is holding right now is
 * frozen as its own snapshot before it is overwritten, so a restore run by mistake can itself be undone.
 *
 * <p>There is no capture verb. Snapshots are taken by the events that make them worth taking, a death and a
 * logout, and one minted on request would sit in the same bounded set and push a real one out of it.
 */
public interface UxmInvRollbackActions {

    /**
     * Restore {@code playerId}'s inventory from the snapshot with this id.
     *
     * <p>The player must be online: a snapshot is applied to a live inventory, never written to disk, so this
     * answers {@code player-offline} for one who is not there. Their snapshots keep, and the restore works once
     * they return. A snapshot id that no longer resolves, pruned or already restored, answers {@code not-found}.
     */
    CompletableFuture<UxmOutcome> restore(UUID playerId, UUID snapshotId);

    /**
     * Restore {@code playerId}'s inventory from the snapshot with this id, now or as soon as they can receive it: the
     * same restore {@code /invrestore} performs for staff.
     *
     * <p>A player online on this server gets it at once, after the same safety copy as {@link #restore}, and this
     * answers {@link UxmRestoreDelivery#APPLIED}. For a player who is not, offline or on another server, a copy of the
     * snapshot is kept and put on them as they next join a server that shares the database, or at once by the server
     * they are on now, again after a safety copy; this answers {@link UxmRestoreDelivery#KEPT}. A kept restore is
     * applied once, and outlives the snapshot it was taken from. A snapshot id that no longer resolves answers
     * {@code not-found}.
     */
    CompletableFuture<UxmResult<UxmRestoreDelivery>> restoreOrKeep(UUID playerId, UUID snapshotId);
}
