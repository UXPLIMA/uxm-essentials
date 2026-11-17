package com.uxplima.uxmessentials.ranks.application.port;

import java.util.List;
import java.util.UUID;

/**
 * Rank action lines kept for a player who was not on the server that ranked them up: they left during the save,
 * moved to another backend, or were promoted while offline. The store is shared by every server on the database, so
 * the lines run on the player's next join wherever that is. Both methods do storage IO and run on the async lane.
 */
public interface PendingRankActions {

    /** Keep {@code lines} for {@code player}, after any already kept, in their order. */
    void keep(UUID player, List<String> lines);

    /**
     * Remove and return every line kept for {@code player}, oldest first. Two servers taking at once each get only
     * the lines they removed, so no line is handed out twice.
     */
    List<String> take(UUID player);
}
