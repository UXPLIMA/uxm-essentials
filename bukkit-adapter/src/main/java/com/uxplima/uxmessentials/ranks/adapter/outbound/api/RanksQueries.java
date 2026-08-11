package com.uxplima.uxmessentials.ranks.adapter.outbound.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmRanksQuery;
import com.uxplima.uxmessentials.api.view.UxmRank;
import com.uxplima.uxmessentials.api.view.UxmRankStanding;
import com.uxplima.uxmessentials.ranks.application.CurrentRank;
import com.uxplima.uxmessentials.ranks.application.port.RankRequirementEvaluator;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.ranks.domain.RankRequirement;
import com.uxplima.uxmessentials.ranks.domain.RankStanding;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The published rank query, over the same ladder and the same stored pointer {@code /rankup} and the {@code /ranks}
 * panel read.
 *
 * <p>The ladder is parsed configuration held in memory, so it answers on the calling thread. A standing is a
 * database read and goes to a worker.
 *
 * <p>{@code canRankUp} is the one that needs care: a requirement can name the player's inventory or a
 * placeholder, and neither can be read off a worker or for somebody who is not there. So it hops to the player's
 * own thread, and a player who is offline reads false, matching the evaluator's own rule that an unverifiable
 * condition fails closed rather than passing on a guess.
 */
@NullMarked
public final class RanksQueries implements UxmRanksQuery {

    private final CurrentRank currentRank;
    private final RankLadder ladder;
    private final RankRequirementEvaluator requirements;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public RanksQueries(
            CurrentRank currentRank,
            RankLadder ladder,
            RankRequirementEvaluator requirements,
            PlayerLookup players,
            Scheduler scheduler) {
        this.currentRank = Objects.requireNonNull(currentRank, "currentRank");
        this.ladder = Objects.requireNonNull(ladder, "ladder");
        this.requirements = Objects.requireNonNull(requirements, "requirements");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public List<UxmRank> ladder() {
        return ladder.ranks().stream().map(RanksQueries::view).toList();
    }

    @Override
    public CompletableFuture<Optional<UxmRankStanding>> standingOf(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(scheduler, () -> currentRank.of(playerId).map(this::view));
    }

    @Override
    public CompletableFuture<Boolean> canRankUp(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!players.isOnline(playerId)) {
            return CompletableFuture.completedFuture(false);
        }
        PlayerRef subject = ApiValues.subject(players, playerId);
        // The stored pointer is a database read, so it happens on a worker; only the requirement check, which may
        // touch the live player, hops to their thread afterwards.
        return AsyncQueries.supply(scheduler, () -> nextRankOf(playerId))
                .thenCompose(next -> next.map(rank -> meets(subject, rank))
                        .orElseGet(() -> CompletableFuture.completedFuture(false)));
    }

    private Optional<Rank> nextRankOf(UUID playerId) {
        return currentRank
                .of(playerId)
                .flatMap(standing -> ladder.next(standing.rank().id()));
    }

    private CompletableFuture<Boolean> meets(PlayerRef subject, Rank rank) {
        CompletableFuture<Boolean> answer = new CompletableFuture<>();
        scheduler.onEntity(subject, () -> {
            try {
                answer.complete(requirements.passesAll(subject, parsed(rank)));
            } catch (RuntimeException failure) {
                answer.completeExceptionally(failure);
            }
        });
        return answer;
    }

    private UxmRankStanding view(RankStanding standing) {
        Optional<UxmRank> next = ladder.next(standing.rank().id()).map(RanksQueries::view);
        return new UxmRankStanding(
                view(standing.rank()), next, standing.prestige().level());
    }

    private static List<RankRequirement> parsed(Rank rank) {
        return rank.requirements().stream()
                .map(RankRequirement::parse)
                .flatMap(Optional::stream)
                .toList();
    }

    private static UxmRank view(Rank rank) {
        return new UxmRank(rank.id().value(), rank.displayName(), rank.order(), rank.cost());
    }
}
