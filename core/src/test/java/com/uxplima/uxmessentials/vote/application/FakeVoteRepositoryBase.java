package com.uxplima.uxmessentials.vote.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;

/**
 * No-op base repository for tests that only need a subset of VoteRepository's methods. Subclasses
 * override the methods they care about.
 */
abstract class FakeVoteRepositoryBase implements VoteRepository {

    @Override
    public int partyCount() {
        return 0;
    }

    @Override
    public void setPartyCount(int count) {}

    @Override
    public int incrementAndGetPartyCount() {
        return 1;
    }

    @Override
    public boolean claimPartyFire(int threshold) {
        return false;
    }

    @Override
    public void enqueue(QueuedReward reward) {}

    @Override
    public List<QueuedReward> drainFor(PlayerRef player) {
        return List.of();
    }

    @Override
    public boolean hasPending(PlayerRef player) {
        return false;
    }

    @Override
    public int queuedCount(PlayerRef player) {
        return 0;
    }

    @Override
    public VoteTally totalsOf(PlayerRef player) {
        return VoteTally.empty();
    }

    @Override
    public void saveTotals(PlayerRef player, VoteTally tally) {}

    @Override
    public List<VoteRanking> topVoters(VotePeriod period, int limit) {
        return List.of();
    }

    @Override
    public void markPartyParticipant(PlayerRef player) {}

    @Override
    public Set<UUID> partyParticipants() {
        return Set.of();
    }

    @Override
    public void clearPartyParticipants() {}

    @Override
    public long partyPeriodKey() {
        return 0;
    }

    @Override
    public void setPartyPeriodKey(long key) {}

    @Override
    public int thresholdOverride() {
        return 0;
    }

    @Override
    public void setThresholdOverride(int override) {}

    @Override
    public void resetTotals(PlayerRef player) {}

    @Override
    public Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
        return Optional.empty();
    }

    @Override
    public void recordLastVoteAtSite(PlayerRef player, String site, Instant at) {}
}
