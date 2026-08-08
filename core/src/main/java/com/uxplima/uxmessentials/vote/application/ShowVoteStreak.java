package com.uxplima.uxmessentials.vote.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.VoteTally;

/**
 * Shows a player's consecutive-day voting streak. Useful for a {@code /vote streak} or
 * {@code /vote streak <player>} command surface: the viewer receives a single message carrying the
 * target's current run and their best run ever.
 */
public final class ShowVoteStreak {

    private final VoteRepository repository;
    private final Notifier notifier;

    public ShowVoteStreak(VoteRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * Resolve the vote tally for {@code target} and send their streak to {@code viewer}. Both viewer
     * and target may be the same player (self-lookup) or different (admin lookup).
     */
    public void show(PlayerRef viewer, PlayerRef target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");

        VoteTally tally = repository.totalsOf(target);
        notifier.send(
                viewer,
                VoteMessageKey.VOTE_STREAK,
                Map.of(
                        "player", target.name(),
                        "current", Long.toString(tally.currentStreak()),
                        "best", Long.toString(tally.bestStreak())));
    }
}
