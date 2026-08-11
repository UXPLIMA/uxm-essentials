package com.uxplima.uxmessentials.api.view;

import java.util.Objects;
import java.util.Optional;

/**
 * Where a player stands on the ladder: the rank they hold, the one above it, and how many times they have
 * prestiged.
 *
 * <p>A player who has never ranked up still has a standing: they hold the first rank, which is what the plugin
 * itself resolves them to. An empty {@code next} means they are at the top, which is the condition a prestige
 * needs.
 *
 * @param rank the rank they currently hold
 * @param next the rank above it, empty at the top of the ladder
 * @param prestige how many times they have prestiged, zero for a player who never has
 */
public record UxmRankStanding(UxmRank rank, Optional<UxmRank> next, int prestige) {

    public UxmRankStanding {
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(next, "next");
        if (prestige < 0) {
            throw new IllegalArgumentException("a prestige level is never negative: " + prestige);
        }
    }

    /** Whether this player is on the top rung, which is what a prestige requires. */
    public boolean atTop() {
        return next.isEmpty();
    }
}
