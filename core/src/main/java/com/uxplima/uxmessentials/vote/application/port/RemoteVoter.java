package com.uxplima.uxmessentials.vote.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Tells the server a voter is on that their rewards are queued, for a vote that arrived on another server of the
 * network. That server pays the queue out at once, the same drain a join runs, so each reward is paid once.
 */
public interface RemoteVoter {

    /** A server with no network: nobody is on another server. */
    RemoteVoter NONE = (voter, serviceName) -> {};

    /** {@code voter}, online on another server, has rewards queued for a vote cast on {@code serviceName}. */
    void rewardsQueued(PlayerRef voter, String serviceName);
}
