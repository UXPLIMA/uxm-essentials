package com.uxplima.uxmessentials.shared.network;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A vote received on the origin backend was credited to a player online on another backend, and their rewards are
 * queued in the shared database. The backend the player is on pays the queue out now instead of at their next join.
 * The frame carries the vote, the player and the vote site only; the queued rows are the source of truth, and the
 * drain removes each row as it pays it, so a backend that pays a reward is the only one that does. With
 * {@code network.transport = both} the frame arrives twice, so the receiver acts on a vote id once. The proxy copy
 * can arrive long after the Redis copy, after the receiver restarted and forgot the id, so a both-carrier frame sent
 * before the receiver started listening is taken for that late copy.
 *
 * @param originServer the backend that received the vote
 * @param voteId names this vote, so a second copy of the frame is told apart from a new vote
 * @param player the voter
 * @param service the vote site the vote came from
 * @param sentAt when the origin sent it, or {@link #UNKNOWN_TIME} on a frame from a peer that sends none
 * @param bothCarriers whether the origin sent the frame on both the proxy and Redis
 */
public record VoteRewardsQueued(
        String originServer, UUID voteId, UUID player, String service, Instant sentAt, boolean bothCarriers)
        implements NetworkMessage {

    /** The send time a frame decodes with when its sender wrote none. */
    public static final Instant UNKNOWN_TIME = Instant.EPOCH;

    /** A vote with no send time, sent on one carrier, as a peer that predates the send time sends it. */
    public VoteRewardsQueued(String originServer, UUID voteId, UUID player, String service) {
        this(originServer, voteId, player, service, UNKNOWN_TIME, false);
    }

    public VoteRewardsQueued {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(voteId, "voteId");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(sentAt, "sentAt");
    }

    /**
     * Whether this is a late copy of a vote a receiver that started listening at {@code listeningSince} has no reason
     * to announce: it went out on both carriers and was sent before then.
     */
    public boolean sentBefore(Instant listeningSince) {
        Objects.requireNonNull(listeningSince, "listeningSince");
        return bothCarriers && !UNKNOWN_TIME.equals(sentAt) && sentAt.isBefore(listeningSince);
    }

    @Override
    public MessageType type() {
        return MessageType.VOTE_REWARDS_QUEUED;
    }
}
