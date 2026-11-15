package com.uxplima.uxmessentials.ranks.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Prestige;
import com.uxplima.uxmessentials.ranks.domain.RankId;

/** A rank pointer store in a map, which counts its reads and can be told to fail like a database that went away. */
final class InMemoryPlayerRanks implements PlayerRankRepository {

    private final Map<UUID, PlayerRank> rows = new HashMap<>();
    private Optional<Optional<PlayerRank>> staleOnce = Optional.empty();
    private int reads;
    private boolean readsFail;
    private boolean savesFail;

    void put(UUID playerId, String rankId, int prestige) {
        rows.put(playerId, new PlayerRank(RankId.of(rankId), new Prestige(prestige)));
    }

    Optional<PlayerRank> row(UUID playerId) {
        return Optional.ofNullable(rows.get(playerId));
    }

    int reads() {
        return reads;
    }

    /** The next read answers {@code pointer}, as a read that began before a later write would. */
    void answerNextReadWith(Optional<PlayerRank> pointer) {
        this.staleOnce = Optional.of(pointer);
    }

    void failReads(boolean fail) {
        this.readsFail = fail;
    }

    void failSaves(boolean fail) {
        this.savesFail = fail;
    }

    @Override
    public Optional<PlayerRank> find(UUID playerId) {
        reads++;
        if (readsFail) {
            throw new IllegalStateException("database unavailable");
        }
        if (staleOnce.isPresent()) {
            Optional<PlayerRank> stale = staleOnce.get();
            staleOnce = Optional.empty();
            return stale;
        }
        return Optional.ofNullable(rows.get(playerId));
    }

    @Override
    public void save(UUID playerId, RankId rankId, Prestige prestige) {
        if (savesFail) {
            throw new IllegalStateException("database unavailable");
        }
        rows.put(playerId, new PlayerRank(rankId, prestige));
    }

    @Override
    public Map<UUID, Integer> prestiged() {
        Map<UUID, Integer> levels = new HashMap<>();
        rows.forEach((player, rank) -> levels.put(player, rank.prestige().level()));
        return levels;
    }
}
