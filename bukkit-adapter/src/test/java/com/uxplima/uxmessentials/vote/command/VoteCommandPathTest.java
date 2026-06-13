package com.uxplima.uxmessentials.vote.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.adapter.VoteServices;
import com.uxplima.uxmessentials.vote.adapter.inbound.command.VoteCommand;
import com.uxplima.uxmessentials.vote.application.ApplyQueuedRewards;
import com.uxplima.uxmessentials.vote.application.HandleVote;
import com.uxplima.uxmessentials.vote.application.HandleVote.VoteRewards;
import com.uxplima.uxmessentials.vote.application.ShowVoteTotals;
import com.uxplima.uxmessentials.vote.application.TopVoters;
import com.uxplima.uxmessentials.vote.application.VoteLinks;
import com.uxplima.uxmessentials.vote.application.VoteMessageKey;
import com.uxplima.uxmessentials.vote.application.VoteNotifier;
import com.uxplima.uxmessentials.vote.application.VotePartyStatus;
import com.uxplima.uxmessentials.vote.application.port.RewardDispatcher;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /vote total} and {@code /vote top} subcommands. Asserts that
 * {@code total} routes to {@link ShowVoteTotals} for the sender (self) and a resolved offline target,
 * that unknown targets receive the {@code VOTE_TOTAL_UNKNOWN} key, that {@code top} routes to
 * {@link TopVoters} with the correct period, and that the {@code top} subcommand is gated by
 * {@code uxmessentials.vote.top}.
 *
 * <p>Both use cases are final classes so they are not subclassed; instead the tests record calls via
 * a recording {@link VoteRepository} and notifier that capture what the use cases read and emit.
 */
class VoteCommandPathTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock sender;
    private PlayerMock target;
    private RecordingVoteRepository repository;
    private RecordingMessages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        sender = server.addPlayer("Alice");
        sender.setOp(true);
        target = server.addPlayer("Bob");
        repository = new RecordingVoteRepository();
        messages = new RecordingMessages();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void voteTotalSelfCallsShowVoteTotalsWithSenderAsBothViewerAndTarget() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote total");

        // ShowVoteTotals.show calls totalsOf on the repository; assert the target UUID is the sender's.
        assertThat(repository.totalsOfCalls).hasSize(1);
        assertThat(repository.totalsOfCalls.get(0).uuid()).isEqualTo(sender.getUniqueId());
    }

    @Test
    void voteTotalOtherResolvesTargetViaPlayerLookup() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote total Bob");

        assertThat(repository.totalsOfCalls).hasSize(1);
        assertThat(repository.totalsOfCalls.get(0).uuid()).isEqualTo(target.getUniqueId());
    }

    @Test
    void voteTotalUnknownPlayerSendsUnknownKey() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote total UnknownPlayer");

        // The repository must not be queried for an unresolvable target.
        assertThat(repository.totalsOfCalls).isEmpty();
        assertThat(messages.lastKey()).isEqualTo(VoteMessageKey.VOTE_TOTAL_UNKNOWN.key());
    }

    @Test
    void voteTopMonthlyIsTheDefaultPeriod() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote top");

        assertThat(repository.topVotersCalls).hasSize(1);
        assertThat(repository.topVotersCalls.get(0)).isEqualTo(VotePeriod.MONTHLY);
    }

    @Test
    void voteTopDailyDispatchesWithDailyPeriod() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote top daily");

        assertThat(repository.topVotersCalls).hasSize(1);
        assertThat(repository.topVotersCalls.get(0)).isEqualTo(VotePeriod.DAILY);
    }

    @Test
    void voteTopWeeklyDispatchesWithWeeklyPeriod() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote top weekly");

        assertThat(repository.topVotersCalls).hasSize(1);
        assertThat(repository.topVotersCalls.get(0)).isEqualTo(VotePeriod.WEEKLY);
    }

    @Test
    void voteTopAlltimeDispatchesWithAlltimePeriod() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote top alltime");

        assertThat(repository.topVotersCalls).hasSize(1);
        assertThat(repository.topVotersCalls.get(0)).isEqualTo(VotePeriod.ALLTIME);
    }

    @Test
    void topSubcommandRequiresVoteTopPermissionNotJustVoteUse() {
        VoteCommand command = new VoteCommand(services(new ServerPlayerLookup()));
        var root = command.build();
        var topNode = root.getChild("top");
        assertThat(topNode).as("top subcommand must exist under /vote").isNotNull();

        // A player with vote.use but not vote.top must not reach the top subcommand.
        PlayerMock restricted = server.addPlayer();
        restricted.addAttachment(plugin, "uxmessentials.vote.use", true);
        assertThat(topNode.canUse(CommandSourceStackMock.from(restricted))).isFalse();

        // A player with vote.top may reach it.
        PlayerMock privileged = server.addPlayer();
        privileged.addAttachment(plugin, "uxmessentials.vote.top", true);
        assertThat(topNode.canUse(CommandSourceStackMock.from(privileged))).isTrue();
    }

    @Test
    void totalSubcommandExistsUnderVote() {
        VoteCommand command = new VoteCommand(services(new ServerPlayerLookup()));
        var root = command.build();
        var totalNode = root.getChild("total");
        assertThat(totalNode).as("total subcommand must exist under /vote").isNotNull();
    }

    @Test
    void leaderboardNameResolverQueriesPlayerLookupByUuid() {
        // Put a ranked player in the repository; the command must resolve the name via PlayerLookup.
        UUID bobUuid = target.getUniqueId();
        // Repository returns Bob's UUID as the ranked player; lookup can resolve it to "Bob".
        repository.topResult = List.of(new VoteRanking(new PlayerRef(bobUuid, bobUuid.toString()), 10L));
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote top monthly");

        // The entry line must carry "Bob" (the resolved name) not the UUID string.
        assertThat(messages.resolvedNames).contains("Bob");
    }

    // --- helpers ---

    private CommandDispatcher<CommandSourceStack> register(PlayerLookup lookup) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CommandRegistration command = new VoteCommand(services(lookup));
        dispatcher.getRoot().addChild(command.build());
        return dispatcher;
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(sender));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private VoteServices services(PlayerLookup lookup) {
        VoteNotifier notifier = new VoteNotifier(messages, new NoSink());
        HandleVote handleVote = new HandleVote(
                repository,
                new NoOpRewardDispatcher(),
                new NoOpVoteAudience(),
                notifier,
                new NoEvents(),
                new VoteRewards(List.of(), List.of(), 25),
                lookup,
                Clock.fixed(Instant.EPOCH, ZoneId.of("UTC")),
                ZoneId.of("UTC"));
        ApplyQueuedRewards applyQueuedRewards = new ApplyQueuedRewards(repository, new NoOpRewardDispatcher());
        VoteLinks voteLinks = new VoteLinks(List.of(), notifier);
        VotePartyStatus votePartyStatus = new VotePartyStatus(repository, notifier, 25);
        ShowVoteTotals showVoteTotals = new ShowVoteTotals(repository, notifier);
        TopVoters topVoters = new TopVoters(repository, notifier, 10);
        return new VoteServices(
                handleVote,
                applyQueuedRewards,
                voteLinks,
                votePartyStatus,
                showVoteTotals,
                topVoters,
                lookup,
                new SyncScheduler(),
                messages);
    }

    /** Resolves online players through the live mock server. */
    private final class ServerPlayerLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.ofNullable(server.getPlayerExact(name))
                    .map(p -> new PlayerRef(p.getUniqueId(), p.getName()));
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.ofNullable(server.getPlayer(uuid)).map(p -> new PlayerRef(p.getUniqueId(), p.getName()));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return server.getPlayer(uuid) != null;
        }
    }

    // --- recording fakes ---

    private static final class RecordingVoteRepository implements VoteRepository {

        final List<PlayerRef> totalsOfCalls = new ArrayList<>();
        final List<VotePeriod> topVotersCalls = new ArrayList<>();
        List<VoteRanking> topResult = List.of();

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
        public VoteTally totalsOf(PlayerRef player) {
            totalsOfCalls.add(player);
            return VoteTally.empty();
        }

        @Override
        public void saveTotals(PlayerRef player, VoteTally tally) {}

        @Override
        public List<VoteRanking> topVoters(VotePeriod period, int limit) {
            topVotersCalls.add(period);
            return topResult;
        }
    }

    /**
     * Records every MessageKey resolved (excluding the shared "prefix" infrastructure key) and
     * collects the {@code player} placeholder values so tests can assert the leaderboard renderer
     * used the real resolved name.
     */
    private static final class RecordingMessages implements Messages {
        final List<String> resolvedKeys = new ArrayList<>();
        final List<String> resolvedNames = new ArrayList<>();

        /** The most recent non-prefix key resolved, or {@code null} if none yet. */
        @Nullable String lastKey() {
            return resolvedKeys.isEmpty() ? null : resolvedKeys.get(resolvedKeys.size() - 1);
        }

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            // Skip the shared "prefix" infrastructure resolve — it is not a feature message.
            if (!"prefix".equals(key.key())) {
                resolvedKeys.add(key.key());
            }
            String playerName = placeholders.get("player");
            if (playerName != null) {
                resolvedNames.add(playerName);
            }
            return key.key();
        }
    }

    // --- minimal stubs ---

    private static final class NoSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(com.uxplima.uxmessentials.shared.domain.Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    private static final class NoOpRewardDispatcher implements RewardDispatcher {
        @Override
        public void dispatch(List<String> commands, String playerName) {}
    }

    private static final class NoOpVoteAudience implements VoteAudience {
        @Override
        public Collection<PlayerRef> online() {
            return List.of();
        }
    }
}
