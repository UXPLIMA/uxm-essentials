package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Hiding a player, or showing them again.
 *
 * <p>The whole of vanish, not a visibility trick: the player is hidden from the tab list, from join and quit
 * messages, from {@code /list} and from the players a level below them, and the buffs their level grants are
 * applied and cleared with it.
 *
 * <p>The level they are hidden at is not something to set. It is resolved from their own permission tier every
 * time they are hidden, so a plugin writing one would be writing a value the next resolve overwrites. Read the
 * level they ended up at through {@code api.vanish().levelOf(playerId)}.
 */
public interface UxmVanishActions {

    /** Hide this player, or show them again. Asking for the state they are already in changes nothing. */
    CompletableFuture<UxmOutcome> setVanished(UUID playerId, boolean vanished);
}
