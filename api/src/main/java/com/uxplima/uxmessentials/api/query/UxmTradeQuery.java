package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmTrade;

/**
 * What is being traded on this server right now.
 *
 * <p>A trade lives in memory for as long as its window is open, so every method here answers on the calling thread
 * with no wait. There is nothing to read for a trade that has ended: a completed swap is an event, not a record
 * this can hand back.
 *
 * <p>Nothing about the offers themselves is published. What is in a trade window changes several times a second
 * and is the two participants' business; a plugin that needs to know what changed hands should listen for the
 * completion event, which carries the totals once they are final.
 */
public interface UxmTradeQuery {

    /** Whether this player has a trade window open. */
    boolean isTrading(UUID playerId);

    /** The trade this player is in, or empty when they are not in one. */
    Optional<UxmTrade> of(UUID playerId);

    /** Every trade open on this server, in no particular order. */
    List<UxmTrade> open();
}
