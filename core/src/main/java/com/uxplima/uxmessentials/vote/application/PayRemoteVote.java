package com.uxplima.uxmessentials.vote.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.domain.event.VoteReceived;

/**
 * Pays a voter online here for a vote another server of the network received: that server queued the rewards in the
 * shared database ({@link HandleVote}), and this one pays the queue out, the same drain a join runs, then publishes
 * {@link VoteReceived} as a vote of an online player does. The drain removes each queued reward as it pays it, so a
 * reward the join or a second copy of the announcement already paid is not paid again, and nothing is published for a
 * drain that paid nothing.
 *
 * <p>Runs off the tick: it reads and writes the database.
 */
public final class PayRemoteVote {

    private final ApplyQueuedRewards queued;
    private final DomainEventPublisher events;

    public PayRemoteVote(ApplyQueuedRewards queued, DomainEventPublisher events) {
        this.queued = Objects.requireNonNull(queued, "queued");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Pay {@code voter} what is queued for them, for a vote cast on {@code serviceName}; whether anything was paid. */
    public boolean pay(PlayerRef voter, String serviceName) {
        Objects.requireNonNull(voter, "voter");
        Objects.requireNonNull(serviceName, "serviceName");
        if (queued.applyFor(voter) == 0) {
            return false;
        }
        events.publish(new VoteReceived(voter, serviceName));
        return true;
    }
}
