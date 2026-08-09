package com.uxplima.uxmessentials.vote.adapter.outbound.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmVoteQuery;
import com.uxplima.uxmessentials.api.view.UxmVoteParty;
import com.uxplima.uxmessentials.api.view.UxmVotePeriod;
import com.uxplima.uxmessentials.api.view.UxmVoteRank;
import com.uxplima.uxmessentials.api.view.UxmVoteTotals;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import org.jspecify.annotations.NullMarked;

/**
 * The published vote query, over the same repository {@code /votes}, {@code /votetop} and {@code /voteparty}
 * read.
 *
 * <p>The party threshold is the effective one: an operator can raise the bar for the current round, and the
 * command shows the raised figure, so this does too. The count is capped at the threshold for the same reason,
 * which is what keeps a progress bar built from these two numbers from overrunning between the vote that crosses
 * the line and the party firing.
 */
@NullMarked
public final class VoteQueries implements UxmVoteQuery {

    private final VoteRepository repository;
    private final PlayerLookup players;
    private final Scheduler scheduler;
    private final int baseThreshold;

    public VoteQueries(VoteRepository repository, PlayerLookup players, Scheduler scheduler, int baseThreshold) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        if (baseThreshold < 1) {
            throw new IllegalArgumentException("baseThreshold must be at least one: " + baseThreshold);
        }
        this.baseThreshold = baseThreshold;
    }

    @Override
    public CompletableFuture<UxmVoteTotals> totals(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(scheduler, () -> view(repository.totalsOf(subject(playerId))));
    }

    @Override
    public CompletableFuture<List<UxmVoteRank>> top(UxmVotePeriod period, int limit) {
        Objects.requireNonNull(period, "period");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least one: " + limit);
        }
        return AsyncQueries.supply(scheduler, () -> ranked(repository.topVoters(domain(period), limit)));
    }

    @Override
    public CompletableFuture<UxmVoteParty> party() {
        return AsyncQueries.supply(scheduler, () -> {
            int threshold = effectiveThreshold();
            return new UxmVoteParty(Math.min(repository.partyCount(), threshold), threshold);
        });
    }

    @Override
    public CompletableFuture<Integer> queuedRewards(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncQueries.supply(scheduler, () -> repository.queuedCount(subject(playerId)));
    }

    private int effectiveThreshold() {
        int override = repository.thresholdOverride();
        return override > 0 ? override : baseThreshold;
    }

    private PlayerRef subject(UUID playerId) {
        return ApiValues.subject(players, playerId);
    }

    private static List<UxmVoteRank> ranked(List<VoteRanking> rows) {
        List<UxmVoteRank> ranks = new ArrayList<>(rows.size());
        int position = 1;
        for (VoteRanking row : rows) {
            ranks.add(new UxmVoteRank(
                    position++, row.player().uuid(), row.player().name(), row.votes()));
        }
        return List.copyOf(ranks);
    }

    private static UxmVoteTotals view(VoteTally tally) {
        return new UxmVoteTotals(
                tally.alltime(),
                tally.daily(),
                tally.weekly(),
                tally.monthly(),
                tally.currentStreak(),
                tally.bestStreak());
    }

    private static VotePeriod domain(UxmVotePeriod period) {
        return switch (period) {
            case DAILY -> VotePeriod.DAILY;
            case WEEKLY -> VotePeriod.WEEKLY;
            case MONTHLY -> VotePeriod.MONTHLY;
            case ALL_TIME -> VotePeriod.ALLTIME;
        };
    }
}
