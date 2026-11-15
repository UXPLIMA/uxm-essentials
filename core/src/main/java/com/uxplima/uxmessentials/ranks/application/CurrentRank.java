package com.uxplima.uxmessentials.ranks.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Prestige;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.ranks.domain.RankStanding;

/**
 * Read use case: resolve a player's current {@link RankStanding}, the ladder {@link Rank} they hold and their
 * {@link Prestige}: from the stored pointer.
 *
 * <p>The rules are deliberately forgiving so the read never fails on operator config drift: a player with no
 * stored pointer stands on the ladder's first rank at prestige zero; a player whose stored id is still on the
 * ladder resolves to that rank; a player whose stored id was removed from the ladder falls back to the first
 * rank but keeps their prestige. An empty ladder yields no standing. There is no rank to stand on, which callers
 * surface as "ranks not configured" rather than a crash.
 *
 * <p>Two reads, split by thread. {@link #of} answers from memory only, so it is safe on a tick thread; it is empty
 * while the player's pointer is not loaded yet. {@link #load} may read the database and is for callers already on
 * the async lane, such as the published API asking about an offline player.
 */
public final class CurrentRank {

    private final CachedPlayerRankRepository pointers;
    private final RankLadder ladder;

    public CurrentRank(CachedPlayerRankRepository pointers, RankLadder ladder) {
        this.pointers = Objects.requireNonNull(pointers, "pointers");
        this.ladder = Objects.requireNonNull(ladder, "ladder");
    }

    /**
     * The player's standing from memory, never blocking. Empty when the ladder holds no ranks, and also when the
     * pointer is not in memory, in which case a background load is started for an online player.
     */
    public Optional<RankStanding> of(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return pointers.cached(playerId).flatMap(this::resolve);
    }

    /** The player's standing, reading the database on a miss. Blocks; call it off the tick thread. */
    public Optional<RankStanding> load(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return resolve(pointers.find(playerId));
    }

    private Optional<RankStanding> resolve(Optional<PlayerRank> stored) {
        if (stored.isEmpty()) {
            return ladder.first().map(rank -> new RankStanding(rank, Prestige.INITIAL));
        }
        PlayerRank pointer = stored.get();
        Optional<Rank> onLadder = ladder.rank(pointer.rankId());
        if (onLadder.isPresent()) {
            return Optional.of(new RankStanding(onLadder.get(), pointer.prestige()));
        }
        return ladder.first().map(rank -> new RankStanding(rank, pointer.prestige()));
    }
}
