package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Redrawing a player's sidebar, and putting it away or bringing it back.
 *
 * <p>The sidebar is redrawn on a timer, so anything that changes what it should say is visible within one refresh
 * interval without asking for anything here. {@link #refresh} is for the cases where the interval is too long a
 * wait: a rank change, a balance the player just earned, a placeholder your own plugin owns and has just moved.
 *
 * <p>Hiding and showing write the same durable preference {@code /scoreboard} flips, so a player who is put away
 * stays that way across a relog until somebody brings it back.
 */
public interface UxmScoreboardActions {

    /** Redraw this player's sidebar now rather than at the next refresh. */
    CompletableFuture<UxmOutcome> refresh(UUID playerId);

    /**
     * Put this player's sidebar away, as {@code /scoreboard} does. Refused with
     * {@link UxmFailure#ALREADY_IN_STATE} when it is already hidden.
     */
    CompletableFuture<UxmOutcome> hide(UUID playerId);

    /**
     * Bring this player's sidebar back. Refused with {@link UxmFailure#ALREADY_IN_STATE} when it is already shown,
     * which is where a player who never touched it starts.
     */
    CompletableFuture<UxmOutcome> show(UUID playerId);
}
