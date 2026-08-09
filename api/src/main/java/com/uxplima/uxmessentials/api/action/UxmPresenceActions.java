package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Marking a player away, or back.
 *
 * <p>A setter rather than a toggle: a plugin that knows somebody is idle wants them away, not flipped. Asking for
 * the state they are already in succeeds and changes nothing, so no announcement goes out twice.
 *
 * <p>Going away is announced and the AFK list updates, exactly as when the player types {@code /afk}. Only a player
 * who is online can be away from a keyboard, so an offline player is {@link UxmFailure#PLAYER_OFFLINE}.
 */
public interface UxmPresenceActions {

    /** Mark this player away, or bring them back. */
    CompletableFuture<UxmOutcome> setAfk(UUID playerId, boolean away);

    /** Mark this player away with a reason others will see next to their name. */
    CompletableFuture<UxmOutcome> setAfk(UUID playerId, String reason);
}
