package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmGameMode;

/**
 * Setting the flags and levels a player carries: god mode, flight, game mode, speed, health, hunger.
 *
 * <p>These are setters rather than toggles, which is the difference between an API and a keystroke. A plugin
 * granting flight for the duration of an event wants flight on, not flight flipped, and asking for a state a player
 * is already in succeeds rather than turning it off.
 *
 * <p>All of it needs somebody at the keyboard: this is state on a live player, not a row in a table. An offline
 * player is {@link UxmFailure#PLAYER_OFFLINE}, and a player who leaves between the call and the write is too, so
 * the future always completes.
 *
 * <p>The player is told what happened, exactly as if a staff member had run the command on them.
 */
public interface UxmPlayerStateActions {

    /** Turn god mode on or off. */
    CompletableFuture<UxmOutcome> setGodMode(UUID playerId, boolean enabled);

    /** Turn flight on or off. Whether they may keep it in a given world is still the world's rule. */
    CompletableFuture<UxmOutcome> setFlying(UUID playerId, boolean enabled);

    /** Put the player in a game mode. */
    CompletableFuture<UxmOutcome> setGameMode(UUID playerId, UxmGameMode mode);

    /**
     * Set the walking speed, on the same scale {@code UxmPlayerState.walkSpeed()} reports: Bukkit's multiplier,
     * where {@code 0.2} is vanilla and {@code 1.0} is the ceiling. Out-of-range values are clamped rather than
     * refused, because a speed nobody can move at is not worth a failure.
     */
    CompletableFuture<UxmOutcome> setWalkSpeed(UUID playerId, float multiplier);

    /** Set the flying speed, on the same scale {@code UxmPlayerState.flySpeed()} reports; {@code 0.1} is vanilla. */
    CompletableFuture<UxmOutcome> setFlySpeed(UUID playerId, float multiplier);

    /** Restore health, and put out any fire they are carrying. */
    CompletableFuture<UxmOutcome> heal(UUID playerId);

    /** Restore hunger and saturation. */
    CompletableFuture<UxmOutcome> feed(UUID playerId);
}
