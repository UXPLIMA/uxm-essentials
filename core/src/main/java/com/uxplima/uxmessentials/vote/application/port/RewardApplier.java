package com.uxplima.uxmessentials.vote.application.port;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.domain.reward.RewardGrant;

/**
 * Outbound port that applies one resolved {@link RewardGrant} for a voter. For an online voter the adapter
 * runs the grant's console commands (substituting {@code {player}}), sends its messages to the voter,
 * broadcasts its broadcast lines, and grants its items. For an offline voter the adapter queues the
 * grant's commands through the existing {@code QueuedReward} mechanism (to drain on the voter's next join)
 * and skips the live messages, broadcasts, and items.
 *
 * <p>This port supersedes the flat {@code RewardDispatcher} for the engine path: the engine resolves a
 * list of grants and {@code HandleVote} applies each one through here.
 */
public interface RewardApplier {

    /** Apply {@code grant} for {@code voter}: dispatch/queue commands and (when online) send messages, broadcasts, and items. */
    void apply(PlayerRef voter, boolean online, RewardGrant grant);

    /**
     * Queue every one of {@code grants} for {@code voter}, who is not online here, then run {@code afterQueued}. It runs
     * once all of them are stored, so whatever it starts (another server paying the queue out) finds every row.
     */
    default void queueAll(PlayerRef voter, List<RewardGrant> grants, Runnable afterQueued) {
        Objects.requireNonNull(voter, "voter");
        Objects.requireNonNull(grants, "grants");
        Objects.requireNonNull(afterQueued, "afterQueued");
        for (RewardGrant grant : grants) {
            apply(voter, false, grant);
        }
        afterQueued.run();
    }
}
