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
 *
 * <p>The threshold placeholders reflect the <em>effective</em> threshold — the stored override when
 * one is active, the configured base otherwise — so they stay accurate under escalation.
 */
@NullMarked
public final class RepositoryVotePlaceholders implements VotePlaceholders {

    private final VoteRepository repository;
    private final int baseThreshold;

    public RepositoryVotePlaceholders(VoteRepository repository, int baseThreshold) {
        this.repository = Objects.requireNonNull(repository, "repository");
        if (baseThreshold < 1) {
            throw new IllegalArgumentException("baseThreshold must be at least one: " + baseThreshold);
        }
        this.baseThreshold = baseThreshold;
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
        int override = repository.thresholdOverride();
        return override > 0 ? override : baseThreshold;
    }
}
