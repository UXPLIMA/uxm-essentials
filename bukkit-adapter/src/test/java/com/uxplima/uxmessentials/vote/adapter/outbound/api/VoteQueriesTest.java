package com.uxplima.uxmessentials.vote.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmVoteParty;
import com.uxplima.uxmessentials.api.view.UxmVotePeriod;
import com.uxplima.uxmessentials.api.view.UxmVoteRank;
import com.uxplima.uxmessentials.api.view.UxmVoteTotals;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published vote query: the counts are the ones {@code /votes} shows, the leaderboard is ranked from one, and
 * the party threshold is the effective one rather than the configured one.
 */
class VoteQueriesTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");
    private static final int BASE_THRESHOLD = 100;

    private FakeVoteRepository repository;
    private QueryDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = new FakeVoteRepository();
        scheduler = new QueryDoubles.InlineScheduler();
    }

    @Test
    void everyReadRunsOffTheCallingThread() {
        queries().totals(ALICE.uuid()).join();
        queries().top(UxmVotePeriod.MONTHLY, 5).join();
        queries().party().join();
        queries().queuedRewards(ALICE.uuid()).join();

        assertThat(scheduler.asyncCalls()).isEqualTo(4);
    }

    @Test
    void theTotalsAreTheOnesTheCommandShows() {
        repository.totals.put(ALICE.uuid(), new VoteTally(42L, 1L, 5L, 20L, 0L, 0L, 0L, 3L, 9L, 0L));

        UxmVoteTotals totals = queries().totals(ALICE.uuid()).join();

        assertThat(totals.allTime()).isEqualTo(42L);
        assertThat(totals.daily()).isEqualTo(1L);
        assertThat(totals.weekly()).isEqualTo(5L);
        assertThat(totals.monthly()).isEqualTo(20L);
        assertThat(totals.currentStreak()).isEqualTo(3L);
        assertThat(totals.bestStreak()).isEqualTo(9L);
        assertThat(totals.hasVoted()).isTrue();
    }

    @Test
    void aPlayerWhoHasNeverVotedReadsAsZeroRatherThanAsAbsent() {
        assertThat(queries().totals(ALICE.uuid()).join()).isEqualTo(UxmVoteTotals.empty());
    }

    @Test
    void theLeaderboardIsRankedFromOneInTheOrderTheRepositoryReturned() {
        repository.top = List.of(new VoteRanking(ALICE, 40L), new VoteRanking(BOB, 12L));

        List<UxmVoteRank> top = queries().top(UxmVotePeriod.WEEKLY, 10).join();

        assertThat(top).extracting(UxmVoteRank::rank).containsExactly(1, 2);
        assertThat(top).extracting(UxmVoteRank::playerName).containsExactly("Alice", "Bob");
        assertThat(top.getFirst().votes()).isEqualTo(40L);
        assertThat(repository.askedPeriod).isEqualTo(VotePeriod.WEEKLY);
    }

    @Test
    void allTimeIsAPeriodTheLeaderboardUnderstands() {
        queries().top(UxmVotePeriod.ALL_TIME, 3).join();

        assertThat(repository.askedPeriod).isEqualTo(VotePeriod.ALLTIME);
    }

    @Test
    void aLeaderboardOfNobodyIsRefusedBeforeAnythingIsScheduled() {
        VoteQueries queries = queries();

        assertThatThrownBy(() -> queries.top(UxmVotePeriod.DAILY, 0).join())
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(scheduler.asyncCalls()).isZero();
    }

    @Test
    void thePartyReportsTheConfiguredThresholdWhenNothingOverridesIt() {
        repository.partyCount = 30;

        UxmVoteParty party = queries().party().join();

        assertThat(party.count()).isEqualTo(30);
        assertThat(party.threshold()).isEqualTo(BASE_THRESHOLD);
        assertThat(party.remaining()).isEqualTo(70);
    }

    @Test
    void anOverriddenThresholdIsWhatThePlayersAreCountingTowards() {
        repository.partyCount = 30;
        repository.thresholdOverride = 50;

        assertThat(queries().party().join().threshold())
                .as("the command shows the raised bar under escalation, so this has to as well")
                .isEqualTo(50);
    }

    @Test
    void theCountNeverOverrunsTheThreshold() {
        repository.partyCount = 120;

        UxmVoteParty party = queries().party().join();

        assertThat(party.count()).isEqualTo(BASE_THRESHOLD);
        assertThat(party.remaining()).isZero();
    }

    @Test
    void queuedRewardsAreTheOnesWaitingForAPlayerToLogIn() {
        repository.queued.put(ALICE.uuid(), 4);

        assertThat(queries().queuedRewards(ALICE.uuid()).join()).isEqualTo(4);
        assertThat(queries().queuedRewards(BOB.uuid()).join()).isZero();
    }

    private VoteQueries queries() {
        return new VoteQueries(
                repository, new QueryDoubles.MapLookup().with(ALICE).with(BOB), scheduler, BASE_THRESHOLD);
    }

    /** Answers the stored counts, and traps every write, every drain and the party bookkeeping. */
    private static final class FakeVoteRepository implements VoteRepository {

        private final Map<UUID, VoteTally> totals = new HashMap<>();
        private final Map<UUID, Integer> queued = new HashMap<>();
        private List<VoteRanking> top = List.of();
        private int partyCount;
        private int thresholdOverride;
        private @Nullable VotePeriod askedPeriod;

        @Override
        public int partyCount() {
            return partyCount;
        }

        @Override
        public void setPartyCount(int count) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public int incrementAndGetPartyCount() {
            throw new AssertionError("a query must never count a vote");
        }

        @Override
        public void enqueue(QueuedReward reward) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public List<QueuedReward> drainFor(PlayerRef player) {
            throw new AssertionError("a query must never pay out a reward");
        }

        @Override
        public boolean hasPending(PlayerRef player) {
            return queued.getOrDefault(player.uuid(), 0) > 0;
        }

        @Override
        public int queuedCount(PlayerRef player) {
            return queued.getOrDefault(player.uuid(), 0);
        }

        @Override
        public VoteTally totalsOf(PlayerRef player) {
            return totals.getOrDefault(player.uuid(), VoteTally.empty());
        }

        @Override
        public void saveTotals(PlayerRef player, VoteTally tally) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public List<VoteRanking> topVoters(VotePeriod period, int limit) {
            askedPeriod = period;
            return new ArrayList<>(top.subList(0, Math.min(limit, top.size())));
        }

        @Override
        public void markPartyParticipant(PlayerRef player) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public Set<UUID> partyParticipants() {
            return Set.of();
        }

        @Override
        public void clearPartyParticipants() {
            throw new AssertionError("a query must never write");
        }

        @Override
        public long partyPeriodKey() {
            return 0L;
        }

        @Override
        public void setPartyPeriodKey(long key) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public int thresholdOverride() {
            return thresholdOverride;
        }

        @Override
        public void setThresholdOverride(int threshold) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public boolean claimPartyFire(int threshold) {
            throw new AssertionError("a query must never fire the party");
        }

        @Override
        public void resetTotals(PlayerRef player) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public java.util.Optional<java.time.Instant> lastVoteAtSite(PlayerRef player, String site) {
            return java.util.Optional.empty();
        }

        @Override
        public void recordLastVoteAtSite(PlayerRef player, String site, java.time.Instant at) {
            throw new AssertionError("a query must never write");
        }
    }
}
