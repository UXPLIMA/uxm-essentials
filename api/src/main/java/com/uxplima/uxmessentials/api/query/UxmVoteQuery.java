package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmVoteParty;
import com.uxplima.uxmessentials.api.view.UxmVotePeriod;
import com.uxplima.uxmessentials.api.view.UxmVoteRank;
import com.uxplima.uxmessentials.api.view.UxmVoteTotals;

/**
 * How much players have voted, and how close the server is to its next party.
 *
 * <p>Vote counts are in the database so they survive a restart, which is why these wait on a read. They answer for
 * a player who is offline: a vote cast for somebody who is not logged in still counts the moment the vote site
 * reports it.
 *
 * <p>Nothing here credits a vote or pays a reward. A consumer that wants to hand out its own reward should listen
 * for the vote event and act on that.
 */
public interface UxmVoteQuery {

    /** This player's vote counts and streaks. Zero everywhere for a player who has never voted. */
    CompletableFuture<UxmVoteTotals> totals(UUID playerId);

    /**
     * The top voters for a period, highest first, at most {@code limit} of them.
     *
     * @throws IllegalArgumentException when {@code limit} is below one
     */
    CompletableFuture<List<UxmVoteRank>> top(UxmVotePeriod period, int limit);

    /** How close the server is to its next vote party. */
    CompletableFuture<UxmVoteParty> party();

    /**
     * How many reward commands are waiting to run for this player: votes that arrived while they were offline and
     * will be paid out on their next join. Zero when nothing is waiting.
     */
    CompletableFuture<Integer> queuedRewards(UUID playerId);
}
