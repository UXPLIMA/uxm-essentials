package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmRank;
import com.uxplima.uxmessentials.api.view.UxmRankStanding;

/**
 * Where players stand on the rank ladder.
 *
 * <p>The ladder itself is configuration and is already in memory, so it answers immediately. A player's standing
 * is a database read, so it waits: it answers for someone who is offline, which is what a web panel or a Discord
 * bot usually wants.
 *
 * <p>Nothing here moves anybody. Promoting is an action ({@code UxmRanksActions}), and it stays that way so a
 * read can never charge a player by accident.
 */
public interface UxmRanksQuery {

    /** The ladder, lowest rung first. Empty when the operator has configured no ranks. */
    List<UxmRank> ladder();

    /**
     * Where this player stands, resolved the same way {@code /rankup} resolves it: a player with no stored rank
     * reads as the first rung. Empty only when the ladder itself is empty.
     */
    CompletableFuture<Optional<UxmRankStanding>> standingOf(UUID playerId);

    /**
     * Whether this player meets everything the rank above them asks for, cost included, so a plugin can show a
     * "ready to rank up" marker without attempting one.
     *
     * <p>False at the top of the ladder, since there is nothing left to meet, and false for a player who is
     * offline: a requirement can name their inventory or a placeholder, and the plugin fails those closed rather
     * than guessing, so an absent player is never reported as ready.
     */
    CompletableFuture<Boolean> canRankUp(UUID playerId);
}
