package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handing kits out.
 *
 * <p>Two verbs, because there are two honest intents. {@link #give} hands the contents over with nothing in the
 * way: no permission, no cooldown, no cost, and a one-time kit stays unclaimed. {@link #claim} runs the player's
 * own path instead, every gate included, and reports which one refused. A rank reward wants the first; a custom
 * {@code /kit} button wants the second.
 *
 * <p>Both need the player online, because the items go into their inventory. An offline player is
 * {@link UxmFailure#PLAYER_OFFLINE}.
 */
public interface UxmKitActions {

    /** Hand this kit's contents to the player, no gates applied. */
    CompletableFuture<UxmOutcome> give(UUID playerId, String kitId);

    /** Claim this kit as the player would, every gate applied. */
    CompletableFuture<UxmOutcome> claim(UUID playerId, String kitId);
}
