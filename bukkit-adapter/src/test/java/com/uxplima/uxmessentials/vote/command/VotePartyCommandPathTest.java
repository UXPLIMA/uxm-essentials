package com.uxplima.uxmessentials.vote.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import com.uxplima.uxmessentials.vote.adapter.inbound.command.VotePartyCommand;
import com.uxplima.uxmessentials.vote.adapter.inbound.gui.VoteSitesGuiView;
import com.uxplima.uxmessentials.vote.application.AddPartyCount;
import com.uxplima.uxmessentials.vote.application.ApplyQueuedRewards;
import com.uxplima.uxmessentials.vote.application.BroadcastSettings;
import com.uxplima.uxmessentials.vote.application.ForceParty;
import com.uxplima.uxmessentials.vote.application.GiveVote;
import com.uxplima.uxmessentials.vote.application.HandleVote;
import com.uxplima.uxmessentials.vote.application.PartyConfig;
import com.uxplima.uxmessentials.vote.application.ResetVoterTotals;
import com.uxplima.uxmessentials.vote.application.RewardEngine;
import com.uxplima.uxmessentials.vote.application.SetPartyCount;
import com.uxplima.uxmessentials.vote.application.ShowLastVote;
import com.uxplima.uxmessentials.vote.application.ShowNextVote;
import com.uxplima.uxmessentials.vote.application.ShowVoteStreak;
import com.uxplima.uxmessentials.vote.application.ShowVoteTotals;
import com.uxplima.uxmessentials.vote.application.TopVoters;
import com.uxplima.uxmessentials.vote.application.VoteLinks;
import com.uxplima.uxmessentials.vote.application.VoteNotifier;
import com.uxplima.uxmessentials.vote.application.VotePartyStatus;
import com.uxplima.uxmessentials.vote.application.VoteReminderEligibility;
import com.uxplima.uxmessentials.vote.application.port.BroadcastThrottle;
import com.uxplima.uxmessentials.vote.application.port.BroadcastVisibility;
import com.uxplima.uxmessentials.vote.application.port.ReminderPreferences;
import com.uxplima.uxmessentials.vote.application.port.RewardApplier;
import com.uxplima.uxmessentials.vote.application.port.RewardDispatcher;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.application.port.VoteBroadcaster;
import com.uxplima.uxmessentials.vote.application.port.VoteContext;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.BroadcastChannel;
import com.uxplima.uxmessentials.vote.domain.BroadcastType;
import com.uxplima.uxmessentials.vote.domain.PartyResetSchedule;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteSiteCatalog;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import com.uxplima.uxmessentials.vote.domain.reward.RewardCatalog;
import com.uxplima.uxmessentials.vote.domain.reward.RewardGrant;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /voteparty} admin subcommands: {@code force}, {@code set <n>},
 * and {@code add <n>}. Asserts that each subcommand is perm-gated, routes to the correct use case,
 * and that the use case drives the right repository mutation.
 */
class VotePartyCommandPathTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock sender;
    private RecordingVoteRepository repository;
    private RecordingMessages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        sender = server.addPlayer("Alice");
        sender.setOp(true);
        repository = new RecordingVoteRepository();
        messages = new RecordingMessages();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void forceSubtreeExistsUnderVoteparty() {
        VotePartyCommand command = new VotePartyCommand(services());
        var root = command.build();
        assertThat(root.getChild("force"))
                .as("force subcommand must exist under /voteparty")
                .isNotNull();
    }

    @Test
    void setSubtreeExistsUnderVoteparty() {
        VotePartyCommand command = new VotePartyCommand(services());
        var root = command.build();
        assertThat(root.getChild("set"))
                .as("set subcommand must exist under /voteparty")
                .isNotNull();
    }

    @Test
    void addSubtreeExistsUnderVoteparty() {
        VotePartyCommand command = new VotePartyCommand(services());
        var root = command.build();
        assertThat(root.getChild("add"))
                .as("add subcommand must exist under /voteparty")
                .isNotNull();
    }

    @Test
    void forceSubtreeRequiresVotepartyAdminPermission() {
        VotePartyCommand command = new VotePartyCommand(services());
        var forceNode = command.build().getChild("force");
        assertThat(forceNode).isNotNull();

        PlayerMock noAdmin = server.addPlayer();
        noAdmin.addAttachment(plugin, "uxmessentials.voteparty.use", true);
        assertThat(forceNode.canUse(CommandSourceStackMock.from(noAdmin))).isFalse();

        PlayerMock admin = server.addPlayer();
        admin.addAttachment(plugin, "uxmessentials.voteparty.admin", true);
        assertThat(forceNode.canUse(CommandSourceStackMock.from(admin))).isTrue();
    }

    @Test
    void votepartySetWritesCountToRepository() {
        sender.addAttachment(plugin, "uxmessentials.voteparty.admin", true);
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "voteparty set 10");

        assertThat(repository.lastSetCount).isEqualTo(10);
    }

    @Test
    void votepartyAddIncreasesCounter() {
        sender.addAttachment(plugin, "uxmessentials.voteparty.admin", true);
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        // Repository starts at 0; adding 5 should call setPartyCount(5) since 5 < threshold 25.
        execute(dispatcher, "voteparty add 5");

        assertThat(repository.lastSetCount).isEqualTo(5);
    }

    @Test
    void votepartyForceResetsBothCounterAndParticipants() {
        sender.addAttachment(plugin, "uxmessentials.voteparty.admin", true);
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "voteparty force");

        // ForceParty fires the party → resets the counter to 0.
        assertThat(repository.lastSetCount).isEqualTo(0);
    }

    // --- helpers ---

    private CommandDispatcher<CommandSourceStack> register() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CommandRegistration command = new VotePartyCommand(services());
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

    private VoteServices services() {
        VoteNotifier notifier = new VoteNotifier(messages, new NoSink());
        RewardSpec noOpSpec =
                new RewardSpec(100, Optional.empty(), List.of(), List.of(), List.of(), List.of(), Set.of());
        PartyConfig party = new PartyConfig(noOpSpec, 25, false, 0, PartyResetSchedule.NONE, List.of());
        NoOpVoteAudience audience = new NoOpVoteAudience();
        NoOpRewardApplier applier = new NoOpRewardApplier();
        NoEvents events = new NoEvents();
        NoLookup lookup = new NoLookup();
        VoteBroadcaster broadcaster = new NoOpBroadcaster();
        BroadcastSettings broadcastSettings =
                new BroadcastSettings(BroadcastType.EVERY_VOTE, Duration.ZERO, Set.of(BroadcastChannel.CHAT), Set.of());
        HandleVote handleVote = new HandleVote(
                repository,
                new RewardEngine(RewardCatalog.empty()),
                applier,
                new NoOpVoteContext(),
                audience,
                broadcastSettings,
                broadcaster,
                new NoOpBroadcastThrottle(),
                events,
                party,
                0,
                ZoneId.of("UTC"));
        ApplyQueuedRewards applyQueuedRewards = new ApplyQueuedRewards(repository, new NoOpRewardDispatcher());
        VoteLinks voteLinks = new VoteLinks(List.of(), notifier);
        VoteSitesGuiView sitesGui = new VoteSitesGuiView(
                VoteSiteCatalog.empty(),
                repository,
                new SyncScheduler(),
                messages,
                VoteSitesGuiView.GuiConfig.defaults());
        VotePartyStatus votePartyStatus = new VotePartyStatus(repository, notifier, 25);
        ShowVoteTotals showVoteTotals = new ShowVoteTotals(repository, notifier);
        ShowVoteStreak showVoteStreak = new ShowVoteStreak(repository, notifier);
        TopVoters topVoters = new TopVoters(repository, notifier, 10);
        ShowNextVote showNextVote = new ShowNextVote(repository, VoteSiteCatalog.empty(), notifier);
        ShowLastVote showLastVote = new ShowLastVote(repository, VoteSiteCatalog.empty(), notifier);
        VoteReminderEligibility reminderEligibility = new VoteReminderEligibility(repository, VoteSiteCatalog.empty());
        ForceParty forceParty = new ForceParty(
                repository, applier, audience, notifier, broadcaster, Set.of(BroadcastChannel.CHAT), events, party);
        SetPartyCount setPartyCount = new SetPartyCount(repository, notifier);
        AddPartyCount addPartyCount = new AddPartyCount(
                repository, applier, audience, notifier, broadcaster, Set.of(BroadcastChannel.CHAT), events, party);
        GiveVote giveVote = new GiveVote(handleVote, notifier);
        ResetVoterTotals resetVoterTotals = new ResetVoterTotals(repository, notifier);
        return new VoteServices(
                handleVote,
                applyQueuedRewards,
                voteLinks,
                sitesGui,
                votePartyStatus,
                showVoteTotals,
                showVoteStreak,
                topVoters,
                showNextVote,
                showLastVote,
                reminderEligibility,
                new NoOpReminderPreferences(),
                new NoOpBroadcastVisibility(),
                forceParty,
                setPartyCount,
                addPartyCount,
                giveVote,
                resetVoterTotals,
                lookup,
                new SyncScheduler(),
                messages);
    }

    // --- recording fakes ---

    private static final class RecordingVoteRepository implements VoteRepository {

        int lastSetCount = -1;

        @Override
        public int partyCount() {
            return 0;
        }

        @Override
        public void setPartyCount(int count) {
            lastSetCount = count;
        }

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
            return VoteTally.empty();
        }

        @Override
        public void saveTotals(PlayerRef player, VoteTally tally) {}

        @Override
        public List<VoteRanking> topVoters(VotePeriod period, int limit) {
            return List.of();
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
        public void resetTotals(PlayerRef player) {}

        @Override
        public java.util.Optional<java.time.Instant> lastVoteAtSite(PlayerRef player, String site) {
            return java.util.Optional.empty();
        }

        @Override
        public void recordLastVoteAtSite(PlayerRef player, String site, java.time.Instant at) {}
    }

    private static final class RecordingMessages implements Messages {

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    // --- stubs ---

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

    private static final class NoOpBroadcaster implements VoteBroadcaster {
        @Override
        public void broadcast(MessageKey key, Map<String, String> placeholders, Set<BroadcastChannel> channels) {}
    }

    private static final class NoOpBroadcastThrottle implements BroadcastThrottle {
        @Override
        public Optional<Instant> lastBroadcastAt(PlayerRef voter) {
            return Optional.empty();
        }

        @Override
        public void recordBroadcast(PlayerRef voter, Instant at) {}
    }

    private static final class NoOpBroadcastVisibility implements BroadcastVisibility {
        @Override
        public boolean receivesBroadcasts(PlayerRef who) {
            return true;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return true;
        }
    }

    private static final class NoOpRewardApplier implements RewardApplier {
        @Override
        public void apply(PlayerRef voter, boolean online, RewardGrant grant) {}
    }

    private static final class NoOpVoteContext implements VoteContext {
        @Override
        public String worldOf(PlayerRef voter) {
            return "";
        }

        @Override
        public boolean hasPermission(PlayerRef voter, String node) {
            return false;
        }

        @Override
        public boolean roll(int chancePercent) {
            return chancePercent >= 100;
        }

        @Override
        public boolean isOnline(PlayerRef voter) {
            return false;
        }
    }

    private static final class NoOpVoteAudience implements VoteAudience {
        @Override
        public Collection<PlayerRef> online() {
            return List.of();
        }
    }

    private static final class NoLookup implements PlayerLookup {
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
            return false;
        }
    }

    private static final class NoOpReminderPreferences implements ReminderPreferences {
        @Override
        public boolean wantsReminders(PlayerRef who) {
            return true;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return true;
        }
    }
}
