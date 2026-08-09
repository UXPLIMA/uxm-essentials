package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmHome;
import com.uxplima.uxmessentials.api.view.UxmLocation;

/**
 * Setting, moving, renaming and removing a player's homes.
 *
 * <p>Homes are numbered from zero, and the number is the identity: {@code set} on a slot that is already taken is
 * {@link UxmFailure#ALREADY_EXISTS} rather than a silent overwrite, and moving one is {@code relocate}. That is
 * the same model the slot grid in {@code /home} shows, so a plugin and the player are looking at one thing.
 *
 * <p>Nothing is charged. Home costs are what the player pays for running the command; a plugin setting a home on
 * their behalf is not the player, so no money moves either way. The player's home limit does still apply, because
 * a home beyond it would be one they cannot see: grant the limit node if a plugin should be able to exceed it.
 *
 * <p>The player is told what happened, in their own language, exactly as if they had run the command. An offline
 * player is told nothing and the write still lands.
 */
public interface UxmHomeActions {

    /**
     * Create a home for this player in {@code slot}, answering the home it created.
     *
     * <p>{@link UxmFailure#ALREADY_EXISTS} when the slot is taken, {@link UxmFailure#REFUSED} when the world is one
     * the operator disabled homes in or the spot is unsafe, and {@link UxmFailure#CANCELLED} when another plugin
     * vetoed it.
     */
    CompletableFuture<UxmResult<UxmHome>> set(UUID ownerId, int slot, UxmLocation location);

    /** Move an existing home to a new place, answering it as it now stands. */
    CompletableFuture<UxmResult<UxmHome>> relocate(UUID ownerId, int slot, UxmLocation location);

    /** Give an existing home a name, which is what the player sees in the grid. */
    CompletableFuture<UxmOutcome> rename(UUID ownerId, int slot, String label);

    /** Remove a home, along with the invitations to it. */
    CompletableFuture<UxmOutcome> delete(UUID ownerId, int slot);
}
