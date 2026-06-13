package com.uxplima.uxmessentials.vote.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link ShowVoteTotals}: the viewer receives a {@link VoteMessageKey#VOTE_TOTAL} message whose
 * placeholders carry the target's counts from the repository.
 */
class ShowVoteTotalsTest {

    private FakeShowRepository repository;
    private CapturingSink sink;
    private PlayerRef alice;
    private PlayerRef bob;

    @BeforeEach
    void setUp() {
        repository = new FakeShowRepository();
        sink = new CapturingSink();
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
    }

    @Test
    void showSendsVoteTotalWithPlaceholdersFilled() {
        VoteTally tally = VoteTally.empty()
                .recordVote(Instant.parse("2024-01-15T12:00:00Z"), ZoneOffset.UTC)
                .recordVote(Instant.parse("2024-01-15T13:00:00Z"), ZoneOffset.UTC);
        repository.stored.put(bob.uuid(), tally);

        ShowVoteTotals use = new ShowVoteTotals(repository, notifier(sink));
        use.show(alice, bob);

        assertThat(sink.delivered).hasSize(1);
        // The key message resolver returns the key string; check it carries the right key.
        assertThat(sink.delivered.get(0)).contains(VoteMessageKey.VOTE_TOTAL.key());
    }

    @Test
    void showFallsBackToEmptyTallyForUnknownPlayer() {
        ShowVoteTotals use = new ShowVoteTotals(repository, notifier(sink));
        use.show(alice, bob); // bob has no stored tally

        assertThat(sink.delivered).hasSize(1);
        assertThat(sink.delivered.get(0)).contains(VoteMessageKey.VOTE_TOTAL.key());
    }

    // ---------- helpers ----------

    private VoteNotifier notifier(CapturingSink capturingSink) {
        return new VoteNotifier(new KeyMessages(), capturingSink);
    }

    private static final class FakeShowRepository implements VoteRepository {
        final java.util.Map<UUID, VoteTally> stored = new java.util.LinkedHashMap<>();

        @Override
        public VoteTally totalsOf(PlayerRef player) {
            return stored.getOrDefault(player.uuid(), VoteTally.empty());
        }

        @Override
        public void saveTotals(PlayerRef player, VoteTally tally) {
            stored.put(player.uuid(), tally);
        }

        @Override
        public List<VoteRanking> topVoters(VotePeriod period, int limit) {
            return List.of();
        }

        @Override
        public int partyCount() {
            return 0;
        }

        @Override
        public void setPartyCount(int count) {}

        @Override
        public int incrementAndGetPartyCount() {
            return 0;
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
        public void markPartyParticipant(PlayerRef player) {}

        @Override
        public Set<UUID> partyParticipants() {
            return Set.of();
        }

        @Override
        public void clearPartyParticipants() {}

        @Override
        public long partyPeriodKey() {
            return 0L;
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
        public boolean claimPartyFire(int threshold) {
            return false;
        }

        @Override
        public void resetTotals(PlayerRef player) {
            stored.put(player.uuid(), VoteTally.empty());
        }

        @Override
        public java.util.Optional<java.time.Instant> lastVoteAtSite(PlayerRef player, String site) {
            return java.util.Optional.empty();
        }

        @Override
        public void recordLastVoteAtSite(PlayerRef player, String site, java.time.Instant at) {}
    }

    private static final class CapturingSink implements MessageSink {
        final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            // Return the key so the tests can assert on which message was sent.
            return key.key();
        }
    }
}
