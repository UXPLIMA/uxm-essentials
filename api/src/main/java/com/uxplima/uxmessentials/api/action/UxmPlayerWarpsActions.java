package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmLocation;

/**
 * Creating, moving, renaming and removing the warps players own.
 *
 * <p>These are the player-made warps, not the server warps an operator sets; those are {@link UxmWarpActions}.
 * Names are server-wide unique, lowercase, three to thirty-two characters of {@code a-z 0-9 _ -}, and a name that
 * does not fit that shape is {@link UxmFailure#REFUSED} rather than an exception.
 *
 * <p>Every verb takes the player it acts as. A warp belongs to somebody, and the plugin's own rules about who may
 * move or remove one are written in terms of that person: the owner, a co-owner, a manager. Passing the owner is
 * the ordinary case; passing anybody else gets exactly the answer they would get in game, which for removal is
 * {@link UxmFailure#REFUSED} for everyone but the owner.
 *
 * <p>Nothing is charged, and no rent is settled. Those follow from a player running the command, and a plugin
 * acting on their behalf is not the player. The owner's warp limit does still apply to a new warp.
 */
public interface UxmPlayerWarpsActions {

    /**
     * Create a warp at {@code where} owned by this player, or re-anchor their own warp of that name in place.
     *
     * <p>{@link UxmFailure#ALREADY_EXISTS} when somebody else holds the name, {@link UxmFailure#REFUSED} when the
     * owner is at their limit, the world is one the operator blacklisted, or the name is reserved or malformed,
     * {@link UxmFailure#NOT_FOUND} when no loaded world has that name, and {@link UxmFailure#CANCELLED} when
     * another plugin vetoed it.
     */
    CompletableFuture<UxmOutcome> create(UUID actorId, String name, UxmLocation where);

    /** Move an existing warp to a new place. {@link UxmFailure#REFUSED} when the actor may not move it. */
    CompletableFuture<UxmOutcome> relocate(UUID actorId, String name, UxmLocation where);

    /** Rename a warp in place, keeping everything else about it. {@link UxmFailure#ALREADY_EXISTS} on a collision. */
    CompletableFuture<UxmOutcome> rename(UUID actorId, String name, String newName);

    /**
     * Retire a warp: it leaves the listings and nobody can travel to it, but it and everything hanging off it
     * survive and {@link #restore} brings it back. This is what {@code /pwarp del} does, and what a plugin
     * cleaning up after a player almost always wants.
     */
    CompletableFuture<UxmOutcome> archive(UUID actorId, String name);

    /** Bring an archived warp back into the listings. */
    CompletableFuture<UxmOutcome> restore(UUID actorId, String name);

    /**
     * Drop a warp for good, freeing its name for reuse. There is no undo: the whitelist, the bans, the earnings
     * and the visit history go with it. {@link UxmFailure#CANCELLED} when another plugin vetoed it.
     */
    CompletableFuture<UxmOutcome> delete(UUID actorId, String name);
}
