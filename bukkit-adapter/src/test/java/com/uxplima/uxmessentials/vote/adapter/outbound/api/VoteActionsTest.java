package com.uxplima.uxmessentials.vote.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import com.uxplima.uxmessentials.vote.application.AddPartyCount;
import com.uxplima.uxmessentials.vote.application.BroadcastSettings;
import com.uxplima.uxmessentials.vote.application.GiveVote;
import com.uxplima.uxmessentials.vote.application.HandleVote;
import com.uxplima.uxmessentials.vote.application.PartyConfig;
import com.uxplima.uxmessentials.vote.application.RewardEngine;
import com.uxplima.uxmessentials.vote.application.VoteMessageKey;
import com.uxplima.uxmessentials.vote.application.port.BroadcastThrottle;
import com.uxplima.uxmessentials.vote.application.port.RewardApplier;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.application.port.VoteBroadcaster;
import com.uxplima.uxmessentials.vote.application.port.VoteContext;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.BroadcastType;
import com.uxplima.uxmessentials.vote.domain.PartyResetSchedule;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import com.uxplima.uxmessentials.vote.domain.reward.RewardCatalog;
import com.uxplima.uxmessentials.vote.domain.reward.RewardGrant;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published vote actions: a credited vote is a real vote, a player who is away still gets theirs, and the
 * party counter reaching its threshold fires the party rather than sitting past it.
 */
class VoteActionsTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final int THRESHOLD = 3;

    private WritingRepository repository;
    private RecordingBroadcaster broadcaster;
    private ActionDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = new WritingRepository();
        broadcaster = new RecordingBroadcaster();
        scheduler = new ActionDoubles.InlineScheduler();
    }

    @Test
    void creditingAVoteRecordsItAgainstThePlayer() {
        UxmOutcome outcome = actions().giveVote(ALICE.uuid()).join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(repository.totalsOf(ALICE).alltime()).isEqualTo(1);
    }

    @Test
    void creditingSeveralRunsTheFlowOncePerVote() {
        actions().giveVote(ALICE.uuid(), 2).join();

        assertThat(repository.totalsOf(ALICE).alltime()).isEqualTo(2);
    }

    @Test
    void aVoteForSomebodyWhoIsAwayIsStillRecorded() {
        UUID stranger = UUID.randomUUID();

        UxmOutcome outcome = actions().giveVote(stranger).join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(repository
                        .totalsOf(new PlayerRef(stranger, stranger.toString()))
                        .alltime())
                .isEqualTo(1);
    }

    @Test
    void anAmountOfNoneIsABugRatherThanAnAnswer() {
        assertThatThrownBy(() -> actions().giveVote(ALICE.uuid(), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> actions().addPartyProgress(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void partyProgressMovesTheCounterWithoutCreditingAnybody() {
        actions().addPartyProgress(2).join();

        assertThat(repository.partyCount()).isEqualTo(2);
        assertThat(repository.totalsOf(ALICE).alltime()).isZero();
    }

    @Test
    void progressThatReachesTheThresholdFiresTheParty() {
        actions().addPartyProgress(THRESHOLD).join();

        assertThat(broadcaster.announced).contains(VoteMessageKey.VOTEPARTY_REACHED);
    }

    @Test
    void everyWriteRunsOffTheCallingThread() {
        actions().giveVote(ALICE.uuid()).join();
        actions().addPartyProgress(1).join();

        assertThat(scheduler.asyncCalls()).isEqualTo(2);
        assertThat(scheduler.entityCalls()).isZero();
    }

    private VoteActions actions() {
        RewardSpec noReward =
                new RewardSpec(100, Optional.empty(), List.of(), List.of(), List.of(), List.of(), Set.of());
        PartyConfig party = new PartyConfig(noReward, THRESHOLD, false, 0, PartyResetSchedule.NONE, List.of());
        NoRewards applier = new NoRewards();
        NobodyOnline audience = new NobodyOnline();
        ActionDoubles.RecordingEvents events = new ActionDoubles.RecordingEvents();
        HandleVote handleVote = new HandleVote(
                repository,
                new RewardEngine(RewardCatalog.empty(), Set.of()),
                applier,
                new AwayVoters(),
                audience,
                new BroadcastSettings(BroadcastType.EVERY_VOTE, Duration.ZERO, Set.of(BroadcastChannel.CHAT), Set.of()),
                broadcaster,
                new NoThrottle(),
                events,
                party,
                0,
                ZoneId.of("UTC"));
        VoteApiWrites writes = new VoteApiWrites(
                new GiveVote(handleVote, ActionDoubles.silentNotifier()),
                new AddPartyCount(
                        repository,
                        applier,
                        audience,
                        ActionDoubles.silentNotifier(),
                        broadcaster,
                        Set.of(BroadcastChannel.CHAT),
                        events,
                        party));
        return new VoteActions(writes, new QueryDoubles.MapLookup().with(ALICE), scheduler, "TestPlugin");
    }

    /** Keeps the totals and the party bookkeeping in memory, which is everything these two use cases touch. */
    private static final class WritingRepository implements VoteRepository {

        private final Map<UUID, VoteTally> totals = new HashMap<>();
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
            return totals.getOrDefault(player.uuid(), VoteTally.empty());
        }

        @Override
        public void saveTotals(PlayerRef player, VoteTally tally) {
            totals.put(player.uuid(), tally);
        }

        @Override
        public List<VoteRanking> topVoters(VotePeriod period, int limit) {
            return List.of();
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
        public void setThresholdOverride(int threshold) {
            thresholdOverride = threshold;
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
        public void resetTotals(PlayerRef player) {
            totals.remove(player.uuid());
        }

        @Override
        public Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
            return Optional.empty();
        }

        @Override
        public void recordLastVoteAtSite(PlayerRef player, String site, Instant at) {}
    }

    /** Remembers what the party announcement said, since nothing else survives a fire. */
    private static final class RecordingBroadcaster implements VoteBroadcaster {

        private final List<MessageKey> announced = new ArrayList<>();

        @Override
        public void broadcast(MessageKey key, Map<String, String> placeholders, Set<BroadcastChannel> channels) {
            announced.add(key);
        }
    }

    private static final class NoRewards implements RewardApplier {

        @Override
        public void apply(PlayerRef voter, boolean online, RewardGrant grant) {}
    }

    private static final class NobodyOnline implements VoteAudience {

        @Override
        public Collection<PlayerRef> online() {
            return List.of();
        }
    }

    /** Every voter is away, which is the case a plugin crediting a vote most often finds. */
    private static final class AwayVoters implements VoteContext {

        @Override
        public String worldOf(PlayerRef voter) {
            return "world";
        }

        @Override
        public boolean hasPermission(PlayerRef voter, String node) {
            return false;
        }

        @Override
        public boolean roll(int chancePercent) {
            return true;
        }

        @Override
        public boolean isOnline(PlayerRef voter) {
            return false;
        }
    }

    private static final class NoThrottle implements BroadcastThrottle {

        @Override
        public Optional<Instant> lastBroadcastAt(PlayerRef voter) {
            return Optional.empty();
        }

        @Override
        public void recordBroadcast(PlayerRef voter, Instant at) {}
    }
}
