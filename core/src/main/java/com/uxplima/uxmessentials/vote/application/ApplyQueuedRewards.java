package com.uxplima.uxmessentials.vote.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.RewardDispatcher;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;

/**
 * Pays out the rewards a player accrued while offline. Run on the player's join: it drains every pending
 * reward batch for them (a transactional select-then-delete in the repository, so each batch pays out
 * exactly once) and dispatches each batch's commands for the now-online player. A player with nothing
 * queued is a cheap no-op — the {@link VoteRepository#hasPending} probe short-circuits before the drain.
 */
public final class ApplyQueuedRewards {

    private final VoteRepository repository;
    private final RewardDispatcher dispatcher;

    public ApplyQueuedRewards(VoteRepository repository, RewardDispatcher dispatcher) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    /** Drain and dispatch every reward batch queued for {@code player}; returns how many batches paid out. */
    public int applyFor(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        if (!repository.hasPending(player)) {
            return 0;
        }
        List<QueuedReward> pending = repository.drainFor(player);
        for (QueuedReward reward : pending) {
            dispatcher.dispatch(reward.commands(), player.name());
        }
        return pending.size();
    }
}
