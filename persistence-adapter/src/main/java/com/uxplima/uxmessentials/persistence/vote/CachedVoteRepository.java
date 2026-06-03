package com.uxplima.uxmessentials.persistence.vote;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;

/**
 * A thin cache decorator over a delegate {@link VoteRepository} for the one hot read: the global vote-party
 * counter. The counter is read on every received vote and on every {@code /voteparty}, so it is held in an
 * {@link AtomicInteger} that loads through the delegate once (a sentinel of {@code -1} until first read) and
 * is updated write-through on {@link #setPartyCount}. The offline queue is not cached — its operations
 * mutate rows (enqueue/drain), so they go straight to the durable delegate, which stays the source of truth.
 */
public final class CachedVoteRepository implements VoteRepository {

    private static final int UNLOADED = -1;

    private final VoteRepository delegate;
    private final AtomicInteger cachedCount = new AtomicInteger(UNLOADED);

    public CachedVoteRepository(VoteRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public int partyCount() {
        int cached = cachedCount.get();
        if (cached != UNLOADED) {
            return cached;
        }
        int loaded = delegate.partyCount();
        cachedCount.set(loaded);
        return loaded;
    }

    @Override
    public void setPartyCount(int count) {
        delegate.setPartyCount(count);
        cachedCount.set(count);
    }

    @Override
    public void enqueue(QueuedReward reward) {
        delegate.enqueue(reward);
    }

    @Override
    public List<QueuedReward> drainFor(PlayerRef player) {
        return delegate.drainFor(player);
    }

    @Override
    public boolean hasPending(PlayerRef player) {
        return delegate.hasPending(player);
    }

    /** Drop the cached counter so the next read reloads it from the database; call on a module reload. */
    public void invalidate() {
        cachedCount.set(UNLOADED);
    }
}
