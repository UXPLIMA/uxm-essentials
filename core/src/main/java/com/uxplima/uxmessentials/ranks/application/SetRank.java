package com.uxplima.uxmessentials.ranks.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Prestige;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;

/**
 * {@code /ranks setrank <player> <rank>}: an administrator sets a player's rank pointer directly, bypassing the
 * requirements, cost and actions a normal {@code /rankup} runs. The move is validated against the ladder — a rank
 * id that is not a rung is refused (an empty result) so an operator typo never writes an unreachable pointer — and
 * the player's prestige level is carried over unchanged, since setting a rank is not a prestige event.
 *
 * <p>This is the deliberate escape hatch for corrections and grants; the requirement/cost/action pipeline lives in
 * {@link Rankup}, not here.
 */
public final class SetRank {

    private final PlayerRankRepository repository;
    private final RankLadder ladder;

    public SetRank(PlayerRankRepository repository, RankLadder ladder) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.ladder = Objects.requireNonNull(ladder, "ladder");
    }

    /**
     * Set {@code playerId}'s rank to {@code rankId}, keeping their prestige, and return the resolved {@link Rank};
     * empty when {@code rankId} is not on the ladder, in which case nothing is written.
     */
    public Optional<Rank> setRank(UUID playerId, RankId rankId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rankId, "rankId");
        Optional<Rank> target = ladder.rank(rankId);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        Prestige prestige = repository.find(playerId).map(PlayerRank::prestige).orElse(Prestige.INITIAL);
        repository.save(playerId, rankId, prestige);
        return target;
    }
}
