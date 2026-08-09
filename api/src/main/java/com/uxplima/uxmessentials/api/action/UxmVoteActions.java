package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Crediting a vote, or moving the vote party along.
 *
 * <p>A credited vote is a real vote. It runs the whole flow a vote arriving from a listing site runs: the streak,
 * the totals, the rewards, the broadcast, the party counter, and the party itself when the counter reaches its
 * threshold. That is the point of publishing it, and the reason to be careful with it: a plugin handing out a
 * hundred votes hands out a hundred votes' worth of rewards.
 *
 * <p>The player does not have to be online. A vote for somebody who is away is queued the same way a real one is,
 * and pays out when they next join.
 *
 * <pre>{@code
 * actions.vote().ifPresent(vote -> vote.giveVote(playerId, 3));
 * }</pre>
 */
public interface UxmVoteActions {

    /** Credit one vote to this player, as if a listing site had sent it. */
    CompletableFuture<UxmOutcome> giveVote(UUID playerId);

    /** Credit {@code amount} votes to this player, one flow each. {@code amount} must be at least one. */
    CompletableFuture<UxmOutcome> giveVote(UUID playerId, int amount);

    /**
     * Add {@code votes} to the vote party counter, firing the party if that reaches the threshold.
     *
     * <p>Progress only: nobody is credited with a vote of their own by this, and no streak moves. {@code votes}
     * must be at least one.
     */
    CompletableFuture<UxmOutcome> addPartyProgress(int votes);
}
