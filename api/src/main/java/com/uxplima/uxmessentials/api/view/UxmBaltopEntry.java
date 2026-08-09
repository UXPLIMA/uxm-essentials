package com.uxplima.uxmessentials.api.view;

import java.util.Objects;
import java.util.UUID;

/**
 * One line of the balance leaderboard.
 *
 * <p>The rank counts from one, so it can be printed as it stands. Players the operator exempted from the
 * leaderboard are not in it at all, which is why the ranks a consumer sees are contiguous rather than gapped.
 *
 * @param rank the position in the leaderboard, counting from one
 * @param playerId the player
 * @param playerName the name last recorded for them
 * @param balance what they hold in the currency the leaderboard was asked for
 */
public record UxmBaltopEntry(int rank, UUID playerId, String playerName, UxmMoney balance) {

    public UxmBaltopEntry {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(playerName, "playerName");
        Objects.requireNonNull(balance, "balance");
        if (rank < 1) {
            throw new IllegalArgumentException("leaderboard rank counts from one: " + rank);
        }
    }
}
