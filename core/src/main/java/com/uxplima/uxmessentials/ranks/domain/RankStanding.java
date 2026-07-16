package com.uxplima.uxmessentials.ranks.domain;

import java.util.Objects;

/**
 * A player's resolved rank standing: the actual ladder {@link Rank} they hold (not a raw id) together with their
 * {@link Prestige}. It is what a read use case hands back once a stored {@link PlayerRank} pointer has been
 * resolved against the current ladder, so callers get the display name, cost and order without re-resolving. A
 * player with no stored pointer stands on the ladder's first rank at prestige zero.
 *
 * @param rank the resolved ladder rank the player holds
 * @param prestige the player's prestige level
 */
public record RankStanding(Rank rank, Prestige prestige) {

    public RankStanding {
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(prestige, "prestige");
    }
}
