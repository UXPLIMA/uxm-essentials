package com.uxplima.uxmessentials.api.query;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Whether a player has put their sidebar away.
 *
 * <p>The same durable preference {@code /scoreboard} flips and the render loop reads, so a consumer showing a
 * "sidebar: on / off" control agrees with what the player actually sees. The read hops to the thread that owns the
 * player, since the preference is stored on them.
 */
public interface UxmScoreboardQuery {

    /**
     * Whether this player has hidden their sidebar, or empty when they are offline.
     *
     * <p>The preference itself survives a relog, but it is stored on the player and only readable while they are
     * here, which is why somebody who is away is an empty answer rather than a default.
     */
    CompletableFuture<Optional<Boolean>> hidden(UUID playerId);
}
