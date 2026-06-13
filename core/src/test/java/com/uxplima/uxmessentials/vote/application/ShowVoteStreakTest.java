package com.uxplima.uxmessentials.vote.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link ShowVoteStreak}: the viewer receives a {@link VoteMessageKey#VOTE_STREAK} message whose
 * placeholders carry the target's current and best consecutive-day streak from the repository.
 */
class ShowVoteStreakTest {

    private StreakRepository repository;
    private CapturingSink sink;
    private PlayerRef alice;
    private PlayerRef bob;

    @BeforeEach
    void setUp() {
        repository = new StreakRepository();
        sink = new CapturingSink();
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
    }

    @Test
    void showSendsVoteStreakWithCurrentAndBestFilled() {
        // Two consecutive days (non-zero epoch-days) → current streak 2, best 2.
        VoteTally tally = VoteTally.empty()
                .recordVote(Instant.ofEpochSecond(86400), ZoneOffset.UTC)
                .recordVote(Instant.ofEpochSecond(86400 * 2), ZoneOffset.UTC);
        repository.stored.put(bob.uuid(), tally);

        ShowVoteStreak use = new ShowVoteStreak(repository, notifier(sink));
        use.show(alice, bob);

        assertThat(sink.deliveries).hasSize(1);
        assertThat(sink.deliveries.get(0)).contains(VoteMessageKey.VOTE_STREAK.key());
        assertThat(sink.deliveries.get(0)).contains("current=2");
        assertThat(sink.deliveries.get(0)).contains("best=2");
    }

    @Test
    void showFallsBackToZeroStreakForUnknownPlayer() {
        ShowVoteStreak use = new ShowVoteStreak(repository, notifier(sink));
        use.show(alice, bob); // bob has no stored tally

        assertThat(sink.deliveries).hasSize(1);
        assertThat(sink.deliveries.get(0)).contains("current=0");
        assertThat(sink.deliveries.get(0)).contains("best=0");
    }

    // ---------- helpers ----------

    private VoteNotifier notifier(CapturingSink capturingSink) {
        return new VoteNotifier(new PlaceholderEchoMessages(), capturingSink);
    }

    private static final class StreakRepository extends FakeVoteRepositoryBase {
        final Map<UUID, VoteTally> stored = new java.util.LinkedHashMap<>();

        @Override
        public VoteTally totalsOf(PlayerRef player) {
            return stored.getOrDefault(player.uuid(), VoteTally.empty());
        }

        @Override
        public void saveTotals(PlayerRef player, VoteTally tally) {
            stored.put(player.uuid(), tally);
        }
    }

    private static final class CapturingSink implements MessageSink {
        final List<String> deliveries = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            deliveries.add(renderedText);
        }
    }

    /** A {@link Messages} fake that echoes every placeholder as {@code key=value} pairs in the delivery. */
    private static final class PlaceholderEchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            StringBuilder sb = new StringBuilder(key.key());
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                sb.append(' ').append(entry.getKey()).append('=').append(entry.getValue());
            }
            return sb.toString();
        }
    }
}
