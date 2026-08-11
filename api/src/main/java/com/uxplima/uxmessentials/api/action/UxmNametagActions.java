package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Redrawing the nametag above a player's head.
 *
 * <p>One verb, for the same reason the tab list has one: which format a player wears is decided by the module from
 * their permissions, world and state, and a reconcile pass re-applies that decision for every online player on a
 * timer. Taking a nametag away from here would last until the next pass, so it is not offered.
 *
 * <p>{@link #refresh} runs that reconcile for one player immediately, which is what a plugin wants after it has
 * given somebody a rank, moved them between teams, or changed a placeholder the nametag reads. The refresh
 * re-selects the format as well as redrawing it, so a player who should now wear a different one does.
 */
public interface UxmNametagActions {

    /** Re-select and redraw this player's nametag now rather than at the next reconcile pass. */
    CompletableFuture<UxmOutcome> refresh(UUID playerId);
}
