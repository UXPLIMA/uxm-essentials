package com.uxplima.uxmessentials.vote.application.port;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * One row in a vote leaderboard: the player and their accumulated vote count for the queried
 * period. Rows are ordered by the repository from highest to lowest count before being returned
 * to the use case.
 *
 * @param player the ranked player
 * @param votes the player's accumulated vote count for the queried period
 */
public record VoteRanking(PlayerRef player, long votes) {

    public VoteRanking {
        Objects.requireNonNull(player, "player");
        if (votes < 0) {
            throw new IllegalArgumentException("votes must not be negative: " + votes);
        }
    }
}
