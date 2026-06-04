package com.uxplima.uxmessentials.vote.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.HandleVote.VoteRewards;
import com.uxplima.uxmessentials.vote.application.port.RewardDispatcher;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.Vote;
import com.uxplima.uxmessentials.vote.domain.event.VotePartyTriggered;
import com.uxplima.uxmessentials.vote.domain.event.VoteReceived;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The vote command paths through the real use cases against in-memory fakes — the same wiring the
 * Votifier listener and join handler drive, minus Bukkit. It proves that a vote for an online voter runs
 * the configured per-vote reward commands (with {@code {player}} substituted) and advances the party
 * counter; that the Nth vote which reaches the threshold dispatches the party rewards to every online
 * audience member, publishes {@link VotePartyTriggered}, and resets the counter to zero; and that a vote
 * for an offline voter queues a {@link QueuedReward} and dispatches nothing, while
 * {@link ApplyQueuedRewards} on that player's join drains and dispatches the queued commands.
 */
class VoteCommandPathTest {

    private FakeVoteRepository repository;
    private RecordingDispatcher dispatcher;
    private FakeAudience audience;
    private VoteNotifier notifier;
    private CapturingEvents events;
    private FakeLookup lookup;
    private PlayerRef alice;
    private PlayerRef bob;

    @BeforeEach
    void setUp() {
        repository = new FakeVoteRepository();
        dispatcher = new RecordingDispatcher();
        audience = new FakeAudience();
        notifier = new VoteNotifier(new KeyMessages(), new CapturingSink());
        events = new CapturingEvents();
        lookup = new FakeLookup();
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
    }

    @Test
    void anOnlineVoteRunsThePerVoteRewardsAndAdvancesTheCounter() {
        lookup.online.add(alice.uuid());
        audience.players.add(alice);

        HandleVote.Outcome outcome =
                handle(perVote("give {player} diamond 1"), party(), 25).handle(voteBy(alice));

        assertThat(outcome.rewardedNow()).isTrue();
        assertThat(outcome.partyTriggered()).isFalse();
        assertThat(dispatcher.dispatched).containsExactly("give Alice diamond 1");
        assertThat(repository.partyCount()).isEqualTo(1);
        assertThat(events.published).hasOnlyElementsOfType(VoteReceived.class);
    }

    @Test
    void theThresholdVoteFiresThePartyForEveryoneAndResetsTheCounter() {
        lookup.online.add(alice.uuid());
        audience.players.add(alice);
        audience.players.add(bob);
        repository.setPartyCount(24); // the next vote is the 25th

        HandleVote.Outcome outcome = handle(perVote("eco give {player} 10"), party("crate give {player} vote 1"), 25)
                .handle(voteBy(alice));

        assertThat(outcome.partyTriggered()).isTrue();
        assertThat(repository.partyCount()).isZero();
        // Alice's per-vote reward, then the party reward for both online players.
        assertThat(dispatcher.dispatched)
                .containsExactly("eco give Alice 10", "crate give Alice vote 1", "crate give Bob vote 1");
        assertThat(events.published)
                .anyMatch(e -> e instanceof VotePartyTriggered triggered && triggered.threshold() == 25);
    }

    @Test
    void anOfflineVoteIsQueuedAndPaysOutOnJoin() {
        // Bob is offline at vote time — nothing is dispatched, a batch is queued.
        HandleVote.Outcome outcome =
                handle(perVote("give {player} apple 3"), party(), 25).handle(voteBy(bob));

        assertThat(outcome.rewardedNow()).isFalse();
        assertThat(dispatcher.dispatched).isEmpty();
        assertThat(repository.hasPending(bob)).isTrue();
        assertThat(repository.partyCount()).isEqualTo(1); // the counter still advances for an offline vote

        // Bob joins: the queued batch drains and dispatches with his name substituted.
        int paid = new ApplyQueuedRewards(repository, dispatcher).applyFor(bob);

        assertThat(paid).isEqualTo(1);
        assertThat(dispatcher.dispatched).containsExactly("give Bob apple 3");
        assertThat(repository.hasPending(bob)).isFalse();
    }

    private HandleVote handle(List<String> perVote, List<String> party, int threshold) {
        return new HandleVote(
                repository,
                dispatcher,
                audience,
                notifier,
                events,
                new VoteRewards(perVote, party, threshold),
                lookup,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    private static Vote voteBy(PlayerRef voter) {
        return new Vote(voter, "TestVoteSite", Instant.EPOCH);
    }

    private static List<String> perVote(String... commands) {
        return List.of(commands);
    }

    private static List<String> party(String... commands) {
        return List.of(commands);
    }

    /** A map/list-backed {@link VoteRepository}: one counter int and a per-player ordered queue. */
    private static final class FakeVoteRepository implements VoteRepository {
        private int count;
        private final Map<UUID, List<QueuedReward>> queue = new LinkedHashMap<>();

        @Override
        public int partyCount() {
            return count;
        }

        @Override
        public void setPartyCount(int count) {
            this.count = count;
        }

        @Override
        public int incrementAndGetPartyCount() {
            return ++count;
        }

        @Override
        public void enqueue(QueuedReward reward) {
            queue.computeIfAbsent(reward.player().uuid(), u -> new ArrayList<>())
                    .add(reward);
        }

        @Override
        public List<QueuedReward> drainFor(PlayerRef player) {
            List<QueuedReward> drained = queue.remove(player.uuid());
            return drained == null ? List.of() : List.copyOf(drained);
        }

        @Override
        public boolean hasPending(PlayerRef player) {
            return queue.containsKey(player.uuid());
        }
    }

    private static final class RecordingDispatcher implements RewardDispatcher {
        private final List<String> dispatched = new ArrayList<>();

        @Override
        public void dispatch(List<String> commands, String playerName) {
            for (String command : commands) {
                dispatched.add(command.replace("{player}", playerName));
            }
        }
    }

    private static final class FakeAudience implements VoteAudience {
        private final List<PlayerRef> players = new ArrayList<>();

        @Override
        public Collection<PlayerRef> online() {
            return List.copyOf(players);
        }
    }

    private static final class FakeLookup implements PlayerLookup {
        private final List<UUID> online = new ArrayList<>();

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return online.contains(uuid);
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class CapturingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // discarded: broadcast delivery is not under assertion here
        }
    }

    private static final class CapturingEvents implements DomainEventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }
}
