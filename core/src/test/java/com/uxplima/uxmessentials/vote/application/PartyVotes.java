package com.uxplima.uxmessentials.vote.application;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;

/** The party side of the vote store in memory: the counter, the window's voters and the threshold override. */
final class PartyVotes implements VoteRepository {

    private final Set<UUID> participants = new HashSet<>();
    private int partyCount;
    private int thresholdOverride;
    private long periodKey;

    @Override
    public int partyCount() {
        return partyCount;
    }

    @Override
    public void setPartyCount(int count) {
        partyCount = count;
    }

    @Override
    public int incrementAndGetPartyCount() {
        return ++partyCount;
    }

    @Override
    public void markPartyParticipant(PlayerRef player) {
        participants.add(player.uuid());
    }

    @Override
    public Set<UUID> partyParticipants() {
        return Set.copyOf(participants);
    }

    @Override
    public void clearPartyParticipants() {
        participants.clear();
    }

    @Override
    public long partyPeriodKey() {
        return periodKey;
    }

    @Override
    public void setPartyPeriodKey(long key) {
        periodKey = key;
    }

    @Override
    public int thresholdOverride() {
        return thresholdOverride;
    }

    @Override
    public void setThresholdOverride(int override) {
        thresholdOverride = override;
    }

    @Override
    public boolean claimPartyFire(int threshold) {
        if (partyCount < threshold) {
            return false;
        }
        partyCount = 0;
        return true;
    }

    @Override
    public void enqueue(QueuedReward reward) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<QueuedReward> drainFor(PlayerRef player) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasPending(PlayerRef player) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int queuedCount(PlayerRef player) {
        throw new UnsupportedOperationException();
    }

    @Override
    public VoteTally totalsOf(PlayerRef player) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveTotals(PlayerRef player, VoteTally tally) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<UUID, VoteTally> allTotals() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<VoteRanking> topVoters(VotePeriod period, int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resetTotals(PlayerRef player) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void recordLastVoteAtSite(PlayerRef player, String site, Instant at) {
        throw new UnsupportedOperationException();
    }
}
