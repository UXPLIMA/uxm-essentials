package com.uxplima.uxmessentials.vote.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;

/**
 * Admin use case: reset all vote totals for a player to zero (alltime, daily, weekly, monthly, and
 * their period keys). This does not affect the vote queue or the global party counter.
 */
public final class ResetVoterTotals {

    private final VoteRepository repository;
    private final Notifier notifier;

    public ResetVoterTotals(VoteRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * Reset all vote totals for {@code target} and notify {@code actor}.
     *
     * @param actor  the admin running the command (receives confirmation)
     * @param target the player whose totals are cleared
     */
    public void reset(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        repository.resetTotals(target);
        notifier.send(actor, VoteMessageKey.VOTE_ADMIN_RESET, Map.of("player", target.name()));
    }
}
