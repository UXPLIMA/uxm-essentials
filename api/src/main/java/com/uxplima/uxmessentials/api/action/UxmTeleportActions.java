package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmLocation;

/**
 * Moving a player.
 *
 * <p>{@link #teleport} is the staff hop, the one {@code /tp} makes: no warmup to stand still for, no cooldown, no
 * fee, no request for the player to accept. It is what a plugin sending somebody to an arena or a dungeon wants,
 * and going through it rather than through Bukkit buys the things that make a teleport safe here: the region hop
 * happens off the tick thread, passengers and vehicles come along, the arrival grace applies, the {@code /back}
 * point is captured, and the teleport event fires so everything watching stays in step.
 *
 * <p>{@link #back} is the player's own return, gates and all: whether returning to a death point is allowed and
 * how long they must wait for it are the operator's settings, not the caller's.
 *
 * <p>A teleport's future completes when the player has landed, not when the hop was asked for. A return's
 * completes when the return has been accepted, since the player may still have a warmup to stand still for and
 * moving during it is theirs to decide.
 */
public interface UxmTeleportActions {

    /**
     * Send this player to a place.
     *
     * <p>{@link UxmFailure#NOT_FOUND} when no loaded world has that name, and {@link UxmFailure#PLAYER_OFFLINE}
     * when there is nobody to move.
     */
    CompletableFuture<UxmOutcome> teleport(UUID playerId, UxmLocation location);

    /**
     * Return this player to where they last were, as {@code /back} would.
     *
     * <p>{@link UxmFailure#NOT_FOUND} when there is nowhere to go back to, and {@link UxmFailure#REFUSED} when
     * there is but a rule says no: a death point on a server that does not allow returning to one, or one they
     * must still wait out.
     */
    CompletableFuture<UxmOutcome> back(UUID playerId);
}
