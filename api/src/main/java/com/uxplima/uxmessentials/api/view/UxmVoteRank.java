package com.uxplima.uxmessentials.api.view;

import java.util.Objects;
import java.util.UUID;

/**
 * One line of a vote leaderboard.
 *
 * <p>The rank counts from one, so it can be printed as it stands, and the count is for the period the leaderboard
 * was asked for rather than for all time.
 *
 * @param rank the position in the leaderboard, counting from one
 * @param playerId the player
 * @param playerName the name last recorded for them
 * @param votes how many votes they cast in the period asked for
 */
public record UxmVoteRank(int rank, UUID playerId, String playerName, long votes) {

    public UxmVoteRank {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
        if (rank < 1) {
            throw new IllegalArgumentException("rank counts from one: " + rank);
        }
        if (votes < 0L) {
            throw new IllegalArgumentException("votes must not be negative: " + votes);
        }
    }
}
