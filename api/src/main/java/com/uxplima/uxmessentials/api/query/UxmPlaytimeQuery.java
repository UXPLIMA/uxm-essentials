package com.uxplima.uxmessentials.api.query;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmPlaytime;

/**
 * How long a player has been on the server.
 *
 * <p>The ledger is in the database, so this waits on a read rather than answering from memory, and it answers for
 * a player who is offline as readily as for one who is online. A player nobody has ever sampled reads as zero
 * rather than as absent, because a rank check that has to tell "no rows" from "no time" would only ever pick zero
 * anyway.
 */
public interface UxmPlaytimeQuery {

    /** This player's playtime across today, the week, the month and all time. */
    CompletableFuture<UxmPlaytime> of(UUID playerId);
}
