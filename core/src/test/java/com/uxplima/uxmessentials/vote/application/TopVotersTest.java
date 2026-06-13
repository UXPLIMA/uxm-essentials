package com.uxplima.uxmessentials.vote.application;

import static org.assertj.core.api.Assertions.assertThat;

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
 * {@link TopVoters}: an empty repository sends the empty-state message; a non-empty result sends
 * the header followed by one entry per ranked player.
 */
class TopVotersTest {

    private FakeTopRepository repository;
    private CapturingSink sink;
    private PlayerRef viewer;
    private PlayerRef alice;
    private PlayerRef bob;

    @BeforeEach
    void setUp() {
        repository = new FakeTopRepository();
        sink = new CapturingSink();
        viewer = new PlayerRef(UUID.randomUUID(), "Viewer");
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
    }

    @Test
    void noVotesForPeriodSendsEmptyMessage() {
        TopVoters use = new TopVoters(repository, notifier(sink), 10);
        use.top(viewer, VotePeriod.ALLTIME);

        assertThat(sink.delivered).hasSize(1);
        assertThat(sink.delivered.get(0)).contains(VoteMessageKey.VOTE_TOP_EMPTY.key());
    }

    @Test
    void withResultsSendsHeaderThenOneEntryPerRow() {
        repository.rows = List.of(new VoteRanking(alice, 10), new VoteRanking(bob, 7));

        TopVoters use = new TopVoters(repository, notifier(sink), 10);
        use.top(viewer, VotePeriod.ALLTIME);

        // header + 2 entries
        assertThat(sink.delivered).hasSize(3);
        assertThat(sink.delivered.get(0)).contains(VoteMessageKey.VOTE_TOP_HEADER.key());
        assertThat(sink.delivered.get(1)).contains(VoteMessageKey.VOTE_TOP_ENTRY.key());
        assertThat(sink.delivered.get(2)).contains(VoteMessageKey.VOTE_TOP_ENTRY.key());
    }

    // ---------- helpers ----------

    private VoteNotifier notifier(CapturingSink capturingSink) {
        return new VoteNotifier(new KeyMessages(), capturingSink);
    }

    private static final class FakeTopRepository implements VoteRepository {
        List<VoteRanking> rows = List.of();

        @Override
        public List<VoteRanking> topVoters(VotePeriod period, int limit) {
            return rows;
        }

        @Override
        public VoteTally totalsOf(PlayerRef player) {
            return VoteTally.empty();
        }

        @Override
        public void saveTotals(PlayerRef player, VoteTally tally) {}

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
        public void resetTotals(PlayerRef player) {}
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
            return key.key();
        }
    }
}
