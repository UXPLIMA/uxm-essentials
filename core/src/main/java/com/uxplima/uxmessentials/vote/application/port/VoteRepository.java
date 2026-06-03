package com.uxplima.uxmessentials.vote.application.port;

import java.util.List;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;

/**
 * Outbound port for the vote context's durable state: the single global vote-party counter and the
 * per-player offline reward queue. Both survive a restart — a vote that arrives while the voter is
 * offline must still pay out when they next join, and an accumulated party count must not reset on a
 * crash — so neither is PDC-backed. A cache decorator may sit in front of the hot counter; the contract
 * here is the durable source of truth.
 *
 * <p>The queue is ordered per player: {@link #enqueue} appends a batch, {@link #drainFor} returns every
 * pending batch for one player and removes those rows in the same transaction (so a reward is paid out
 * exactly once), and {@link #hasPending} is the cheap existence probe the join handler runs first.
 */
public interface VoteRepository {

    /** The current accumulated vote-party count; zero when no party is in progress. */
    int partyCount();

    /** Persist the accumulated vote-party count (the single global counter row). */
    void setPartyCount(int count);

    /** Append a reward batch for an offline voter to that player's queue. */
    void enqueue(QueuedReward reward);

    /** Return and remove every pending reward batch for {@code player}, in queued order. */
    List<QueuedReward> drainFor(PlayerRef player);

    /** True when {@code player} has at least one pending reward batch. */
    boolean hasPending(PlayerRef player);
}
