package com.uxplima.uxmessentials.vote.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.HandleVote.PartyReward;
import com.uxplima.uxmessentials.vote.application.port.RewardApplier;
import com.uxplima.uxmessentials.vote.application.port.RewardDispatcher;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.application.port.VoteContext;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.Vote;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import com.uxplima.uxmessentials.vote.domain.event.VotePartyTriggered;
import com.uxplima.uxmessentials.vote.domain.event.VoteReceived;
import com.uxplima.uxmessentials.vote.domain.reward.RewardCatalog;
import com.uxplima.uxmessentials.vote.domain.reward.RewardGrant;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The vote command paths through the real {@link HandleVote} use case against in-memory fakes — the same
 * wiring the Votifier listener and join handler drive, minus Bukkit. It proves that a vote for an online
 * voter records the tally, resolves the per-vote reward set through the {@link RewardEngine}, and applies
 * each grant; that the Nth vote which reaches the threshold pays the party reward to every online audience
 * member, publishes {@link VotePartyTriggered}, and resets the counter to zero; and that a vote for an
 * offline voter has its grants applied to the offline applier (which queues the commands) while
 * {@link ApplyQueuedRewards} on that player's join drains and dispatches the queued commands.
 */
class VoteCommandPathTest {

    private FakeVoteRepository repository;
    private RecordingApplier applier;
    private RecordingDispatcher dispatcher;
    private FakeAudience audience;
    private VoteNotifier notifier;
    private CapturingEvents events;
    private FakeVoteContext context;
    private PlayerRef alice;
    private PlayerRef bob;

    @BeforeEach
    void setUp() {
        repository = new FakeVoteRepository();
        applier = new RecordingApplier();
        dispatcher = new RecordingDispatcher();
        audience = new FakeAudience();
        notifier = new VoteNotifier(new KeyMessages(), new CapturingSink());
        events = new CapturingEvents();
        context = new FakeVoteContext();
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
    }

    @Test
    void anOnlineVoteRecordsTheTallyResolvesTheRewardSetAndAdvancesTheCounter() {
        context.online.add(alice.uuid());
        audience.players.add(alice);

        HandleVote.Outcome outcome =
                handle(catalogPerVote("give {player} diamond 1"), party(), 25).handle(voteBy(alice));

        assertThat(outcome.rewardedNow()).isTrue();
        assertThat(outcome.partyTriggered()).isFalse();
        assertThat(applier.commandsFor(alice)).containsExactly("give {player} diamond 1");
        assertThat(applier.appliedOnline(alice)).isTrue();
        assertThat(repository.totalsOf(alice).alltime()).isEqualTo(1);
        assertThat(repository.partyCount()).isEqualTo(1);
        assertThat(events.published).hasOnlyElementsOfType(VoteReceived.class);
    }

    @Test
    void theThresholdVoteFiresThePartyForEveryoneAndResetsTheCounter() {
        context.online.add(alice.uuid());
        audience.players.add(alice);
        audience.players.add(bob);
        repository.setPartyCount(24); // the next vote is the 25th

        HandleVote.Outcome outcome = handle(
                        catalogPerVote("eco give {player} 10"), party("crate give {player} vote 1"), 25)
                .handle(voteBy(alice));

        assertThat(outcome.partyTriggered()).isTrue();
        assertThat(repository.partyCount()).isZero();
        // Alice gets her per-vote grant; both online players get the party grant.
        assertThat(applier.commandsFor(alice)).containsExactly("eco give {player} 10", "crate give {player} vote 1");
        assertThat(applier.commandsFor(bob)).containsExactly("crate give {player} vote 1");
        assertThat(events.published)
                .anyMatch(e -> e instanceof VotePartyTriggered triggered && triggered.threshold() == 25);
    }

    @Test
    void aVoteAlwaysRecordsTotalsRegardlessOfOnlineStatus() {
        context.online.add(alice.uuid());
        audience.players.add(alice);

        handle(catalogPerVote("give {player} diamond 1"), party(), 25).handle(voteBy(alice));
        assertThat(repository.totalsOf(alice).alltime()).isEqualTo(1);

        // Bob is offline; his tally is still updated.
        handle(catalogPerVote("give {player} apple 3"), party(), 25).handle(voteBy(bob));
        assertThat(repository.totalsOf(bob).alltime()).isEqualTo(1);
    }

    @Test
    void anOfflineVoteIsAppliedOfflineThenPaysOutOnJoin() {
        // Bob is offline at vote time — the grant is applied offline (the real applier queues its commands).
        HandleVote.Outcome outcome =
                handle(catalogPerVote("give {player} apple 3"), party(), 25).handle(voteBy(bob));

        assertThat(outcome.rewardedNow()).isFalse();
        assertThat(applier.appliedOnline(bob)).isFalse();
        assertThat(applier.commandsFor(bob)).containsExactly("give {player} apple 3");
        assertThat(repository.partyCount()).isEqualTo(1); // the counter still advances for an offline vote

        // The offline applier queued the grant's commands; on join ApplyQueuedRewards drains them.
        repository.enqueue(new QueuedReward(bob, List.of("give {player} apple 3"), Instant.EPOCH));
        int paid = new ApplyQueuedRewards(repository, dispatcher).applyFor(bob);

        assertThat(paid).isEqualTo(1);
        assertThat(dispatcher.dispatched).containsExactly("give Bob apple 3");
        assertThat(repository.hasPending(bob)).isFalse();
    }

    private HandleVote handle(RewardCatalog catalog, List<String> partyCommands, int threshold) {
        return new HandleVote(
                repository,
                new RewardEngine(catalog),
                applier,
                context,
                audience,
                notifier,
                events,
                new PartyReward(partyCommands, threshold),
                ZoneOffset.UTC);
    }

    private static RewardCatalog catalogPerVote(String... commands) {
        RewardSpec spec =
                new RewardSpec(100, Optional.empty(), List.of(commands), List.of(), List.of(), List.of(), Set.of());
        return new RewardCatalog(List.of(spec), Map.of(), List.of(), List.of());
    }

    private static Vote voteBy(PlayerRef voter) {
        return new Vote(voter, "TestVoteSite", Instant.EPOCH);
    }

    private static List<String> party(String... commands) {
        return List.of(commands);
    }

    /** Captures every {@link RewardGrant} applied, per voter, with the online flag it was applied under. */
    private static final class RecordingApplier implements RewardApplier {
        private final Map<UUID, List<String>> commands = new LinkedHashMap<>();
        private final Map<UUID, Boolean> online = new LinkedHashMap<>();

        @Override
        public void apply(PlayerRef voter, boolean isOnline, RewardGrant grant) {
            commands.computeIfAbsent(voter.uuid(), u -> new ArrayList<>()).addAll(grant.commands());
            online.put(voter.uuid(), isOnline);
        }

        List<String> commandsFor(PlayerRef voter) {
            return commands.getOrDefault(voter.uuid(), List.of());
        }

        boolean appliedOnline(PlayerRef voter) {
            return Boolean.TRUE.equals(online.get(voter.uuid()));
        }
    }

    /** A deterministic {@link VoteContext}: online by membership, all permissions granted, every roll passes. */
    private static final class FakeVoteContext implements VoteContext {
        private final List<UUID> online = new ArrayList<>();

        @Override
        public String worldOf(PlayerRef voter) {
            return online.contains(voter.uuid()) ? "world" : "";
        }

        @Override
        public boolean hasPermission(PlayerRef voter, String node) {
            return true;
        }

        @Override
        public boolean roll(int chancePercent) {
            return chancePercent > 0;
        }

        @Override
        public boolean isOnline(PlayerRef voter) {
            return online.contains(voter.uuid());
        }
    }

    /** A map/list-backed {@link VoteRepository}: one counter int and a per-player ordered queue. */
    private static final class FakeVoteRepository implements VoteRepository {
        private int count;
        private final Map<UUID, List<QueuedReward>> queue = new LinkedHashMap<>();
        private final Map<UUID, VoteTally> tallies = new LinkedHashMap<>();

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

        @Override
        public VoteTally totalsOf(PlayerRef player) {
            return tallies.getOrDefault(player.uuid(), VoteTally.empty());
        }

        @Override
        public void saveTotals(PlayerRef player, VoteTally tally) {
            tallies.put(player.uuid(), tally);
        }

        @Override
        public List<VoteRanking> topVoters(VotePeriod period, int limit) {
            return List.of();
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
