package com.uxplima.uxmessentials.vote.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
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
import com.uxplima.uxmessentials.vote.application.port.RewardApplier;
import com.uxplima.uxmessentials.vote.application.port.RewardDispatcher;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.application.port.VoteContext;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.PartyResetSchedule;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.Vote;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import com.uxplima.uxmessentials.vote.domain.event.VotePartyTriggered;
import com.uxplima.uxmessentials.vote.domain.event.VoteReceived;
import com.uxplima.uxmessentials.vote.domain.reward.RewardCatalog;
import com.uxplima.uxmessentials.vote.domain.reward.RewardGrant;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;
import org.jspecify.annotations.Nullable;
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

        HandleVote.Outcome outcome = handle(
                        catalogPerVote("give {player} diamond 1"), partyConfig("give {player} crate 1", 25))
                .handle(voteBy(alice));

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
                        catalogPerVote("eco give {player} 10"), partyConfig("crate give {player} vote 1", 25))
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

        handle(catalogPerVote("give {player} diamond 1"), partyConfig("", 25)).handle(voteBy(alice));
        assertThat(repository.totalsOf(alice).alltime()).isEqualTo(1);

        // Bob is offline; his tally is still updated.
        handle(catalogPerVote("give {player} apple 3"), partyConfig("", 25)).handle(voteBy(bob));
        assertThat(repository.totalsOf(bob).alltime()).isEqualTo(1);
    }

    @Test
    void anOfflineVoteIsAppliedOfflineThenPaysOutOnJoin() {
        // Bob is offline at vote time — the grant is applied offline (the real applier queues its commands).
        HandleVote.Outcome outcome = handle(catalogPerVote("give {player} apple 3"), partyConfig("", 25))
                .handle(voteBy(bob));

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

    @Test
    void partyFiresOnlyToVotersWhenOnlyVotersIsTrue() {
        context.online.add(alice.uuid());
        audience.players.add(alice);
        audience.players.add(bob); // bob is online but did not vote this window
        repository.setPartyCount(24);

        HandleVote.Outcome outcome = handle(
                        catalogPerVote("eco give {player} 10"), partyConfigOnlyVoters("crate give {player} vote 1", 25))
                .handle(voteBy(alice));

        assertThat(outcome.partyTriggered()).isTrue();
        // Alice voted so she gets the party reward; Bob did not vote so he doesn't.
        assertThat(applier.commandsFor(alice)).contains("crate give {player} vote 1");
        assertThat(applier.commandsFor(bob)).doesNotContain("crate give {player} vote 1");
    }

    @Test
    void partyFiresForEveryoneWhenOnlyVotersIsFalse() {
        context.online.add(alice.uuid());
        audience.players.add(alice);
        audience.players.add(bob); // bob is online but did not vote
        repository.setPartyCount(24);

        HandleVote.Outcome outcome = handle(
                        catalogPerVote("eco give {player} 10"), partyConfig("crate give {player} vote 1", 25))
                .handle(voteBy(alice));

        assertThat(outcome.partyTriggered()).isTrue();
        // Both Alice and Bob get the party reward regardless of whether they voted.
        assertThat(applier.commandsFor(alice)).contains("crate give {player} vote 1");
        assertThat(applier.commandsFor(bob)).containsExactly("crate give {player} vote 1");
    }

    @Test
    void thresholdEscalatesAfterPartyFires() {
        context.online.add(alice.uuid());
        audience.players.add(alice);
        repository.setPartyCount(24);

        // escalateBy = 10, so after the party fires the override becomes 25 + 10 = 35
        PartyConfig config =
                new PartyConfig(alwaysSpec("party cmd"), 25, false, 10, PartyResetSchedule.NONE, List.of());
        handle(catalogPerVote("per-vote"), config).handle(voteBy(alice));

        assertThat(repository.thresholdOverride()).isEqualTo(35);
    }

    @Test
    void resetScheduleDailyResetsCounterOnNewDay() {
        context.online.add(alice.uuid());
        audience.players.add(alice);

        // Simulate votes on two different epoch-days via direct counter manipulation
        PartyConfig config = new PartyConfig(
                alwaysSpec("party cmd"),
                50, // high threshold so it doesn't fire
                false,
                0,
                PartyResetSchedule.DAILY,
                List.of());

        // Vote 1: sets up period key for day 0 (Instant.EPOCH = 1970-01-01)
        repository.setPartyCount(5);
        repository.setPartyPeriodKey(0L); // day 0

        // The next vote arrives on day 1 (86400 seconds later)
        Instant day1 = Instant.ofEpochSecond(86400);
        HandleVote handler = new HandleVote(
                repository,
                new RewardEngine(catalogPerVote("cmd")),
                applier,
                context,
                audience,
                notifier,
                events,
                config,
                ZoneOffset.UTC);
        handler.handle(new Vote(alice, "site", day1));

        // Counter should have reset to 0 then been incremented to 1 (not 6)
        assertThat(repository.partyCount()).isEqualTo(1);
    }

    @Test
    void announceAtBroadcastsWhenCountHitsMilestone() {
        context.online.add(alice.uuid());
        audience.players.add(alice);
        audience.players.add(bob);

        CapturingSink broadcastSink = new CapturingSink();
        VoteNotifier broadcastNotifier = new VoteNotifier(new KeyMessages(), broadcastSink);

        // announceAt = [5], threshold = 10; counter starts at 4 so the next vote hits 5
        PartyConfig config =
                new PartyConfig(alwaysSpec("party cmd"), 10, false, 0, PartyResetSchedule.NONE, List.of(5));
        repository.setPartyCount(4);

        HandleVote handler = new HandleVote(
                repository,
                new RewardEngine(catalogPerVote("per-vote")),
                applier,
                context,
                audience,
                broadcastNotifier,
                events,
                config,
                ZoneOffset.UTC);
        HandleVote.Outcome outcome = handler.handle(voteBy(alice));

        assertThat(outcome.partyTriggered()).isFalse();
        // The announce should have been sent to each online player (alice + bob = 2 announce messages).
        // The thanks broadcast also fires (2 messages), so filter by key to assert only on announces.
        long announceCount = broadcastSink.deliveries.stream()
                .filter(s -> s.contains("voteparty.announce"))
                .count();
        assertThat(announceCount).isEqualTo(2);
    }

    @Test
    void forcePartyFiresImmediately() {
        context.online.add(alice.uuid());
        audience.players.add(alice);
        repository.setPartyCount(5); // well below threshold

        PartyConfig config = partyConfig("party cmd", 25);
        ForceParty forceParty = new ForceParty(repository, applier, audience, notifier, events, config);
        forceParty.execute(alice);

        assertThat(repository.partyCount()).isZero();
        assertThat(applier.commandsFor(alice)).contains("party cmd");
        assertThat(events.published).anyMatch(e -> e instanceof VotePartyTriggered);
    }

    @Test
    void setPartyCountPersistsWithoutFiring() {
        SetPartyCount setPartyCount = new SetPartyCount(repository, notifier);
        setPartyCount.set(alice, 10);

        assertThat(repository.partyCount()).isEqualTo(10);
        // No party event published
        assertThat(events.published).noneMatch(e -> e instanceof VotePartyTriggered);
    }

    @Test
    void addPartyCountFiresWhenThresholdReached() {
        context.online.add(alice.uuid());
        audience.players.add(alice);
        repository.setPartyCount(20);

        AddPartyCount addPartyCount =
                new AddPartyCount(repository, applier, audience, notifier, events, partyConfig("party cmd", 25));
        addPartyCount.add(alice, 5);

        assertThat(repository.partyCount()).isZero();
        assertThat(events.published).anyMatch(e -> e instanceof VotePartyTriggered);
    }

    @Test
    void addPartyCountBelowThresholdOnlyUpdatesCounter() {
        repository.setPartyCount(10);

        AddPartyCount addPartyCount =
                new AddPartyCount(repository, applier, audience, notifier, events, partyConfig("party cmd", 25));
        addPartyCount.add(alice, 3);

        assertThat(repository.partyCount()).isEqualTo(13);
        assertThat(events.published).noneMatch(e -> e instanceof VotePartyTriggered);
    }

    @Test
    void giveVoteRunsFullHandleVoteFlowNTimes() {
        context.online.add(alice.uuid());
        audience.players.add(alice);

        HandleVote handleVote = handle(catalogPerVote("give {player} diamond 1"), partyConfig("", 100));
        GiveVote giveVote = new GiveVote(handleVote, notifier);
        giveVote.give(alice, alice, 3);

        assertThat(repository.totalsOf(alice).alltime()).isEqualTo(3);
        assertThat(repository.partyCount()).isEqualTo(3);
    }

    @Test
    void resetVoterTotalsClearsPlayerTotals() {
        repository.saveTotals(alice, VoteTally.empty().recordVote(Instant.EPOCH, ZoneOffset.UTC));
        assertThat(repository.totalsOf(alice).alltime()).isEqualTo(1);

        ResetVoterTotals reset = new ResetVoterTotals(repository, notifier);
        reset.reset(alice, alice);

        assertThat(repository.totalsOf(alice).alltime()).isZero();
    }

    // -------------------------------------------------------------------------
    // Fix C: atomic party-fire claim
    // -------------------------------------------------------------------------

    @Test
    void claimPartyFireReturnsTrueOnceAndFalseOnSecondCall() {
        repository.setPartyCount(25);
        // First claim wins the reset.
        assertThat(repository.claimPartyFire(25)).isTrue();
        assertThat(repository.partyCount()).isZero();
        // Second call at the same threshold finds count=0 < 25 → does not reset, returns false.
        assertThat(repository.claimPartyFire(25)).isFalse();
        assertThat(repository.partyCount()).isZero();
    }

    @Test
    void handleVoteCounterIsZeroAfterThresholdCrossedViaClaim() {
        context.online.add(alice.uuid());
        audience.players.add(alice);
        repository.setPartyCount(24);

        HandleVote.Outcome outcome =
                handle(catalogPerVote("cmd"), partyConfig("party cmd", 25)).handle(voteBy(alice));

        assertThat(outcome.partyTriggered()).isTrue();
        // claimPartyFire atomically reset the counter; no separate setPartyCount(0) in fire().
        assertThat(repository.partyCount()).isZero();
    }

    @Test
    void forcePartyResetsCounterExplicitlyBeforeFire() {
        context.online.add(alice.uuid());
        audience.players.add(alice);
        repository.setPartyCount(10);

        new ForceParty(repository, applier, audience, notifier, events, partyConfig("cmd", 25)).execute(alice);

        assertThat(repository.partyCount()).isZero();
        assertThat(events.published).anyMatch(e -> e instanceof VotePartyTriggered);
    }

    // -------------------------------------------------------------------------
    // Fix A: escalation clearable
    // -------------------------------------------------------------------------

    @Test
    void setPartyCountClearsThresholdOverride() {
        repository.setThresholdOverride(35);

        new SetPartyCount(repository, notifier).set(alice, 0);

        assertThat(repository.thresholdOverride()).isZero();
    }

    @Test
    void escalateByZeroFireClearsExistingOverride() {
        context.online.add(alice.uuid());
        audience.players.add(alice);
        // Override is 35 from a prior escalation; set count to 34 so the next vote crosses it.
        repository.setPartyCount(34);
        repository.setThresholdOverride(35);

        // escalateBy=0: party fires at the effective threshold (35) and must clear the override.
        PartyConfig config = new PartyConfig(alwaysSpec("party cmd"), 25, false, 0, PartyResetSchedule.NONE, List.of());
        HandleVote.Outcome outcome = handle(catalogPerVote("cmd"), config).handle(voteBy(alice));

        assertThat(outcome.partyTriggered()).isTrue();
        assertThat(repository.thresholdOverride()).isZero();
    }

    // -------------------------------------------------------------------------
    // Fix B: effective threshold in status and PAPI
    // -------------------------------------------------------------------------

    @Test
    void votePartyStatusShowsEscalatedThreshold() {
        repository.setThresholdOverride(40);
        repository.setPartyCount(10);

        CapturingSink sink = new CapturingSink();
        // Use a Messages implementation that embeds placeholder values in the delivery so we can
        // assert on the actual threshold and remaining figures rather than the raw key name.
        VoteNotifier statusNotifier = new VoteNotifier(new PlaceholderEchoMessages(), sink);
        // baseThreshold=25; override=40 → effective=40, remaining=30
        VotePartyStatus status = new VotePartyStatus(repository, statusNotifier, 25);
        status.show(alice);

        // Delivery must embed the effective threshold (40) and remaining (30), not the base (25).
        assertThat(sink.deliveries).anyMatch(s -> s.contains("threshold=40") && s.contains("remaining=30"));
    }

    private HandleVote handle(RewardCatalog catalog, PartyConfig config) {
        return new HandleVote(
                repository,
                new RewardEngine(catalog),
                applier,
                context,
                audience,
                notifier,
                events,
                config,
                ZoneOffset.UTC);
    }

    private static PartyConfig partyConfig(String command, int threshold) {
        return new PartyConfig(alwaysSpec(command), threshold, false, 0, PartyResetSchedule.NONE, List.of());
    }

    private static PartyConfig partyConfigOnlyVoters(String command, int threshold) {
        return new PartyConfig(alwaysSpec(command), threshold, true, 0, PartyResetSchedule.NONE, List.of());
    }

    private static RewardSpec alwaysSpec(String command) {
        List<String> cmds = command.isEmpty() ? List.of() : List.of(command);
        return new RewardSpec(100, Optional.empty(), cmds, List.of(), List.of(), List.of(), Set.of());
    }

    private static RewardCatalog catalogPerVote(String... commands) {
        RewardSpec spec =
                new RewardSpec(100, Optional.empty(), List.of(commands), List.of(), List.of(), List.of(), Set.of());
        return new RewardCatalog(List.of(spec), Map.of(), List.of(), List.of());
    }

    private static Vote voteBy(PlayerRef voter) {
        return new Vote(voter, "TestVoteSite", Instant.EPOCH);
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
        private long periodKey;
        private int thresholdOverride;
        private final Set<UUID> participants = new HashSet<>();
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
        public boolean claimPartyFire(int threshold) {
            if (count >= threshold) {
                count = 0;
                return true;
            }
            return false;
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
            this.periodKey = key;
        }

        @Override
        public int thresholdOverride() {
            return thresholdOverride;
        }

        @Override
        public void setThresholdOverride(int override) {
            this.thresholdOverride = override;
        }

        @Override
        public void resetTotals(PlayerRef player) {
            tallies.put(player.uuid(), VoteTally.empty());
        }

        @Override
        public Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
            return Optional.empty();
        }

        @Override
        public void recordLastVoteAtSite(PlayerRef player, String site, Instant at) {
            // not tracked in the command-path fake
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
        private final List<String> deliveries = new ArrayList<>();

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

    private static final class CapturingEvents implements DomainEventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    @Test
    void handleVoteRecordsLastVoteAtSiteForTheServiceName() {
        context.online.add(alice.uuid());
        audience.players.add(alice);

        TrackingSiteRepository trackingRepo = new TrackingSiteRepository();
        HandleVote handler = new HandleVote(
                trackingRepo,
                new RewardEngine(catalogPerVote("give {player} diamond 1")),
                applier,
                context,
                audience,
                notifier,
                events,
                partyConfig("", 25),
                ZoneOffset.UTC);

        handler.handle(new Vote(alice, "TopVoter", Instant.EPOCH));

        assertThat(trackingRepo.recordedSite).isEqualTo("TopVoter");
        assertThat(trackingRepo.recordedAt).isEqualTo(Instant.EPOCH);
        assertThat(trackingRepo.recordedPlayer).isEqualTo(alice);
    }

    /** Standalone repository that captures recordLastVoteAtSite calls. */
    private static final class TrackingSiteRepository extends FakeVoteRepositoryBase {
        @Nullable String recordedSite;

        @Nullable Instant recordedAt;

        @Nullable PlayerRef recordedPlayer;

        @Override
        public Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
            return Optional.empty();
        }

        @Override
        public void recordLastVoteAtSite(PlayerRef player, String site, Instant at) {
            this.recordedPlayer = player;
            this.recordedSite = site;
            this.recordedAt = at;
        }
    }
}
