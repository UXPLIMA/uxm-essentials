package com.uxplima.uxmessentials.persistence.vote;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;

/**
 * A thin cache decorator over a delegate {@link VoteRepository} for the one hot read: the global vote-party
 * counter. The counter is read on every received vote and on every {@code /voteparty}, so it is held in an
 * {@link AtomicInteger} that loads through the delegate once (a sentinel of {@code -1} until first read) and
 * is updated write-through on {@link #setPartyCount}. The offline queue and vote totals are not cached —
 * queue operations mutate rows (enqueue/drain) and totals are updated on every vote, so they go straight to
 * the durable delegate which stays the source of truth. The leaderboard query ({@link #topVoters}) is also
 * uncached, as it is a bounded query that must reflect the latest data.
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
    public int incrementAndGetPartyCount() {
        int durable = delegate.incrementAndGetPartyCount();
        // The durable store is the authority for the increment; the cache adopts whatever value it returned.
        cachedCount.set(durable);
        return durable;
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

    @Override
    public int queuedCount(PlayerRef player) {
        return delegate.queuedCount(player);
    }

    @Override
    public VoteTally totalsOf(PlayerRef player) {
        return delegate.totalsOf(player);
    }

    @Override
    public void saveTotals(PlayerRef player, VoteTally tally) {
        delegate.saveTotals(player, tally);
    }

    @Override
    public List<VoteRanking> topVoters(VotePeriod period, int limit) {
        return delegate.topVoters(period, limit);
    }

    @Override
    public boolean claimPartyFire(int threshold) {
        boolean won = delegate.claimPartyFire(threshold);
        if (won) {
            // The durable store now holds 0; align the cache so subsequent partyCount() reads are correct.
            cachedCount.set(0);
        }
        return won;
    }

    // Party participants — not cached: membership set mutates on every vote and after each party
    // fires, so the overhead of invalidation is higher than the cost of the small index scan.

    @Override
    public void markPartyParticipant(PlayerRef player) {
        delegate.markPartyParticipant(player);
    }

    @Override
    public Set<UUID> partyParticipants() {
        return delegate.partyParticipants();
    }

    @Override
    public void clearPartyParticipants() {
        delegate.clearPartyParticipants();
    }

    // Party period key — not cached: read once per period-boundary check, no hot-path pressure.

    @Override
    public long partyPeriodKey() {
        return delegate.partyPeriodKey();
    }

    @Override
    public void setPartyPeriodKey(long key) {
        delegate.setPartyPeriodKey(key);
    }

    // Threshold override — not cached: written by admin commands, read only at threshold evaluation.

    @Override
    public int thresholdOverride() {
        return delegate.thresholdOverride();
    }

    @Override
    public void setThresholdOverride(int override) {
        delegate.setThresholdOverride(override);
    }

    // Per-site cooldown — delegates straight through; reads are infrequent (one per incoming vote per
    // site), so the round-trip to the delegate is cheaper than the cache-invalidation overhead.

    @Override
    public Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
        return delegate.lastVoteAtSite(player, site);
    }

    @Override
    public void recordLastVoteAtSite(PlayerRef player, String site, Instant at) {
        delegate.recordLastVoteAtSite(player, site, at);
    }

    // Admin reset — delegates straight through; invalidates no counter cache (totals are uncached).

    @Override
    public void resetTotals(PlayerRef player) {
        delegate.resetTotals(player);
    }

    /** Drop the cached counter so the next read reloads it from the database; call on a module reload. */
    public void invalidate() {
        cachedCount.set(UNLOADED);
    }
}
