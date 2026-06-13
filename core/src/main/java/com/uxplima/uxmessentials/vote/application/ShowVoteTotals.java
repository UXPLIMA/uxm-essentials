package com.uxplima.uxmessentials.vote.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.VoteTally;

/**
 * Shows a player's accumulated vote totals across all tracked periods. Useful for a
 * {@code /votes} or {@code /votes <player>} command surface: the viewer receives a single
 * message containing the target's daily, weekly, monthly, and all-time counts.
 */
public final class ShowVoteTotals {

    private final VoteRepository repository;
    private final VoteNotifier notifier;

    public ShowVoteTotals(VoteRepository repository, VoteNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * Resolve the vote tally for {@code target} and send it to {@code viewer}. Both viewer and
     * target may be the same player (self-lookup) or different (admin lookup).
     */
    public void show(PlayerRef viewer, PlayerRef target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");

        VoteTally tally = repository.totalsOf(target);
        notifier.send(
                viewer,
                VoteMessageKey.VOTE_TOTAL,
                Map.of(
                        "player", target.name(),
                        "daily", Long.toString(tally.daily()),
                        "weekly", Long.toString(tally.weekly()),
                        "monthly", Long.toString(tally.monthly()),
                        "alltime", Long.toString(tally.alltime()),
                        "current_streak", Long.toString(tally.currentStreak()),
                        "best_streak", Long.toString(tally.bestStreak())));
    }
}
