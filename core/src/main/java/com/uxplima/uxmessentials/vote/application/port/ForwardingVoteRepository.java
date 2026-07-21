package com.uxplima.uxmessentials.vote.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;

/**
 * A plain forwarding decorator over a {@link VoteRepository}: every method delegates to the wrapped
 * {@code delegate} unchanged. A concrete decorator overrides only the handful of methods it augments (a
 * cache write-through, a bus announcement) and inherits the pure pass-throughs, so it carries no
 * boilerplate for the calls it leaves untouched. Modelled on Guava's {@code ForwardingObject}.
 */
public abstract class ForwardingVoteRepository implements VoteRepository {

    /** The wrapped repository every un-overridden method forwards to. */
    protected final VoteRepository delegate;

    protected ForwardingVoteRepository(VoteRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public int partyCount() {
        return delegate.partyCount();
    }

    @Override
    public void setPartyCount(int count) {
        delegate.setPartyCount(count);
    }

    @Override
    public int incrementAndGetPartyCount() {
        return delegate.incrementAndGetPartyCount();
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
        return delegate.claimPartyFire(threshold);
    }

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

    @Override
    public long partyPeriodKey() {
        return delegate.partyPeriodKey();
    }

    @Override
    public void setPartyPeriodKey(long key) {
        delegate.setPartyPeriodKey(key);
    }

    @Override
    public int thresholdOverride() {
        return delegate.thresholdOverride();
    }

    @Override
    public void setThresholdOverride(int override) {
        delegate.setThresholdOverride(override);
    }

    @Override
    public Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
        return delegate.lastVoteAtSite(player, site);
    }

    @Override
    public void recordLastVoteAtSite(PlayerRef player, String site, Instant at) {
        delegate.recordLastVoteAtSite(player, site, at);
    }

    @Override
    public void resetTotals(PlayerRef player) {
        delegate.resetTotals(player);
    }
}
