package com.uxplima.uxmessentials.ranks.domain.event;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.ranks.domain.RankId;

/**
 * A player's rank was set directly by an administrator, bypassing the requirements, the cost and the rank's
 * actions. Raised after the pointer is stored.
 *
 * <p>Carries a UUID rather than a player reference because the target does not have to be online: correcting a
 * rank is exactly the kind of thing done while somebody is away.
 *
 * @param playerId the account whose rank was set
 * @param previous the rank they held, empty when the ladder had nothing to resolve for them
 * @param rank the rank they now hold
 */
public record PlayerRankSet(UUID playerId, Optional<RankId> previous, RankId rank) implements RankEvent {

    public PlayerRankSet {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(rank, "rank");
    }
}
