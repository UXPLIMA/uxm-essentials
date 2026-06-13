package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import org.jspecify.annotations.NullMarked;

/**
 * {@link VotePlaceholders} over the vote context's read ports: the {@link VoteRepository} tally and
 * party counter. Built during vote wiring from the same repository the {@code HandleVote} use case
 * holds, so the placeholder counts match what the leaderboard and total commands display.
 */
@NullMarked
public final class RepositoryVotePlaceholders implements VotePlaceholders {

    private final VoteRepository repository;
    private final int threshold;

    public RepositoryVotePlaceholders(VoteRepository repository, int threshold) {
        this.repository = Objects.requireNonNull(repository, "repository");
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold must be at least one: " + threshold);
        }
        this.threshold = threshold;
    }

    @Override
    public long countFor(PlayerRef who, VotePeriod period) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(period, "period");
        return repository.totalsOf(who).countFor(period);
    }

    @Override
    public int partyCount() {
        return repository.partyCount();
    }

    @Override
    public int partyThreshold() {
        return threshold;
    }
}
