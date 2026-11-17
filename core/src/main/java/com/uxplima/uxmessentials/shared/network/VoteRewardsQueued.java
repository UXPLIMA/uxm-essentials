package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A vote received on the origin backend was credited to a player online on another backend, and their rewards are
 * queued in the shared database. The backend the player is on pays the queue out now instead of at their next join.
 * The frame carries the player and the vote site only; the queued rows are the source of truth, and the drain removes
 * each row as it pays it, so a backend that pays a reward is the only one that does.
 *
 * @param originServer the backend that received the vote
 * @param player the voter
 * @param service the vote site the vote came from
 */
public record VoteRewardsQueued(String originServer, UUID player, String service) implements NetworkMessage {

    public VoteRewardsQueued {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(service, "service");
    }

    @Override
    public MessageType type() {
        return MessageType.VOTE_REWARDS_QUEUED;
    }
}
