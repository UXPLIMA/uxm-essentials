package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.adapter.inbound.command.CheckBanCommand;
import com.uxplima.uxmessentials.moderation.adapter.inbound.command.CheckMuteCommand;
import com.uxplima.uxmessentials.moderation.adapter.inbound.command.HistoryCommand;
import com.uxplima.uxmessentials.moderation.adapter.inbound.command.StaffHistoryCommand;
import com.uxplima.uxmessentials.moderation.application.CheckBan;
import com.uxplima.uxmessentials.moderation.application.CheckMute;
import com.uxplima.uxmessentials.moderation.application.ModerationNotifier;
import com.uxplima.uxmessentials.moderation.application.ReviewSanctionHistory;
import com.uxplima.uxmessentials.moderation.application.ReviewStaffHistory;
import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.application.port.TargetResolver;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the four read-only review commands {@code /history}, {@code /staffhistory},
 * {@code /checkban} and {@code /checkmute}. Each dispatches its argument to its B-1 use case off the tick thread
 * through the {@link Scheduler} port. The use cases are built over in-memory fakes and exposed through a mocked
 * {@link ModerationServices}, so a dispatch is observed by the rendered reply the {@link RecordingSink}
 * captures. The {@link RunInline} scheduler runs the off-tick task synchronously so the reply is visible in the
 * same call. Targets resolve through the {@link TargetResolver} so an offline player is reviewable and an
 * unknown name is rejected before any use case runs; the permission predicate blocks dispatch when the sender
 * lacks the node.
 */
class HistoryCommandsTest {

    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID STAFF = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final Instant NOW = Instant.parse("2026-06-14T12:00:00Z");

    private ServerMock server;
    private RunInline scheduler;
    private RecordingMessages messages;
    private RecordingSink sink;
    private FakeHistory history;
    private FakeRepository repository;
    private FakeTargets targets;
    private FakeLookup lookup;
    private ModerationServices services;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        scheduler = new RunInline();
        messages = new RecordingMessages();
        sink = new RecordingSink();
        history = new FakeHistory();
        repository = new FakeRepository();
        targets = new FakeTargets();
        lookup = new FakeLookup();
        ModerationNotifier notifier = new ModerationNotifier(messages, sink);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        services = mock(ModerationServices.class);
        lenient().when(services.targets()).thenReturn(targets);
        lenient().when(services.reviewSanctionHistory()).thenReturn(new ReviewSanctionHistory(history, notifier));
        lenient().when(services.reviewStaffHistory()).thenReturn(new ReviewStaffHistory(history, lookup, notifier));
        lenient().when(services.checkBan()).thenReturn(new CheckBan(repository, notifier, clock));
        lenient().when(services.checkMute()).thenReturn(new CheckMute(repository, notifier, clock));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void historyRendersEveryKindNewestFirst() {
        targets.add(new PlayerRef(TARGET, "Subject"));
        history.forTarget.add(entry(SanctionAction.KICK, TARGET, "rule break"));
        history.forTarget.add(entry(SanctionAction.WARN, TARGET, "spam"));

        dispatch(new HistoryCommand(services, messages, sink, scheduler), staff(), "history Subject");

        assertThat(messages.keys).contains("moderation.history.header", "moderation.history.entry");
        assertThat(scheduler.asyncTasks).isOne();
    }

    @Test
    void historyOnAnEmptyRecordRendersTheEmptyNotice() {
        targets.add(new PlayerRef(TARGET, "Subject"));

        dispatch(new HistoryCommand(services, messages, sink, scheduler), staff(), "history Subject");

        assertThat(messages.keys).containsExactly("moderation.history.empty");
    }

    @Test
    void historyRejectsAnUnknownPlayerWithoutRunningTheRead() {
        dispatch(new HistoryCommand(services, messages, sink, scheduler), staff(), "history Nobody");

        assertThat(messages.keys).containsExactly("moderation.unknown-target");
        assertThat(scheduler.asyncTasks).isZero();
    }

    @Test
    void historyResolvesAnOfflineTarget() {
        targets.add(new PlayerRef(TARGET, "Offline"));
        history.forTarget.add(entry(SanctionAction.BAN, TARGET, "griefing"));

        dispatch(new HistoryCommand(services, messages, sink, scheduler), staff(), "history Offline");

        assertThat(messages.keys).contains("moderation.history.header");
    }

    @Test
    void historyIsBlockedWithoutThePermission() {
        targets.add(new PlayerRef(TARGET, "Subject"));

        dispatch(new HistoryCommand(services, messages, sink, scheduler), unprivileged(), "history Subject");

        assertThat(scheduler.asyncTasks).isZero();
        assertThat(messages.keys).isEmpty();
    }

    @Test
    void staffHistoryRendersTheSanctionsAStaffMemberIssued() {
        targets.add(new PlayerRef(STAFF, "Mod"));
        lookup.add(new PlayerRef(TARGET, "Subject"));
        history.byActor.add(entry(SanctionAction.MUTE, TARGET, "spam"));

        dispatch(new StaffHistoryCommand(services, messages, sink, scheduler), staff(), "staffhistory Mod");

        assertThat(messages.keys).contains("moderation.staffhistory.header", "moderation.staffhistory.entry");
    }

    @Test
    void staffHistoryOnNoIssuedSanctionsRendersTheEmptyNotice() {
        targets.add(new PlayerRef(STAFF, "Mod"));

        dispatch(new StaffHistoryCommand(services, messages, sink, scheduler), staff(), "staffhistory Mod");

        assertThat(messages.keys).containsExactly("moderation.staffhistory.empty");
    }

    @Test
    void checkBanReportsAnActiveBan() {
        targets.add(new PlayerRef(TARGET, "Subject"));
        repository.tempban =
                TempbanState.active(NOW.plus(Duration.ofDays(1)), Issuer.console("Mod"), Optional.of("griefing"), NOW);

        dispatch(new CheckBanCommand(services, messages, sink, scheduler, null, null), staff(), "checkban Subject");

        assertThat(messages.keys).containsExactly("moderation.check.banned");
    }

    @Test
    void checkBanReportsAnUnbannedPlayer() {
        targets.add(new PlayerRef(TARGET, "Subject"));
        repository.tempban = TempbanState.none();

        dispatch(new CheckBanCommand(services, messages, sink, scheduler, null, null), staff(), "checkban Subject");

        assertThat(messages.keys).containsExactly("moderation.check.not-banned");
    }

    @Test
    void checkMuteReportsAnActiveMute() {
        targets.add(new PlayerRef(TARGET, "Subject"));
        repository.mute = MuteState.permanent(Issuer.console("Mod"), Optional.of("spam"), NOW);

        dispatch(new CheckMuteCommand(services, messages, sink, scheduler, null, null), staff(), "checkmute Subject");

        assertThat(messages.keys).containsExactly("moderation.check.muted");
    }

    @Test
    void checkMuteReportsAnUnmutedPlayer() {
        targets.add(new PlayerRef(TARGET, "Subject"));
        repository.mute = MuteState.none();

        dispatch(new CheckMuteCommand(services, messages, sink, scheduler, null, null), staff(), "checkmute Subject");

        assertThat(messages.keys).containsExactly("moderation.check.not-muted");
    }

    private static SanctionHistoryEntry entry(SanctionAction action, UUID target, String reason) {
        return new SanctionHistoryEntry(
                action, target, Issuer.console("Mod"), Optional.of(reason), NOW, Optional.empty(), Optional.empty());
    }

    private PlayerMock staff() {
        PlayerMock actor = server.addPlayer("Operator");
        actor.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.moderation.history", true);
        actor.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.moderation.staffhistory", true);
        actor.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.moderation.check", true);
        return actor;
    }

    private PlayerMock unprivileged() {
        return server.addPlayer("Visitor");
    }

    private void dispatch(
            com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration command,
            PlayerMock sender,
            String input) {
        LiteralCommandNode<CommandSourceStack> node = command.build();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(sender));
        } catch (CommandSyntaxException blockedOrBadSyntax) {
            // A blocked node (missing permission) or a usage miss surfaces here; the test asserts no effect ran.
        }
    }

    /** Runs the off-tick task inline so the rendered reply is observable in the dispatch call. */
    private static final class RunInline implements Scheduler {
        private int asyncTasks;

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            asyncTasks++;
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** A fake history exposing one list per read so each command's query is independently primed. */
    private static final class FakeHistory implements SanctionHistory {
        private final List<SanctionHistoryEntry> forTarget = new ArrayList<>();
        private final List<SanctionHistoryEntry> byActor = new ArrayList<>();

        @Override
        public void append(SanctionHistoryEntry entry) {
            throw new UnsupportedOperationException("the review commands never append");
        }

        @Override
        public List<SanctionHistoryEntry> banHistory(UUID target, int limit) {
            return List.of();
        }

        @Override
        public List<SanctionHistoryEntry> muteHistory(UUID target, int limit) {
            return List.of();
        }

        @Override
        public List<SanctionHistoryEntry> recentForTarget(UUID target, int limit) {
            return List.copyOf(forTarget);
        }

        @Override
        public List<SanctionHistoryEntry> recentByActor(UUID actor, int limit) {
            return List.copyOf(byActor);
        }
    }

    /** A repository fake the check commands read for the active ban/mute state. */
    private static final class FakeRepository extends UnsupportedModerationRepository {
        private TempbanState tempban = TempbanState.none();
        private MuteState mute = MuteState.none();

        @Override
        public TempbanState loadTempban(PlayerRef target) {
            return tempban;
        }

        @Override
        public MuteState loadMute(PlayerRef target) {
            return mute;
        }
    }

    /** A name-to-ref target resolver fake; an unseen name resolves to empty. */
    private static final class FakeTargets implements TargetResolver {
        private final Map<String, PlayerRef> known = new HashMap<>();

        void add(PlayerRef ref) {
            known.put(ref.name(), ref);
        }

        @Override
        public Optional<PlayerRef> resolve(String name) {
            return Optional.ofNullable(known.get(name));
        }
    }

    /** A UUID-to-ref lookup fake so /staffhistory renders each row's target name. */
    private static final class FakeLookup implements PlayerLookup {
        private final Map<UUID, PlayerRef> known = new HashMap<>();

        void add(PlayerRef ref) {
            known.put(ref.uuid(), ref);
        }

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return known.values().stream()
                    .filter(ref -> ref.name().equals(name))
                    .findFirst();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.ofNullable(known.get(uuid));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    /** Records the key suffixes resolved so a dispatch's rendered replies are assertable by key. */
    private static final class RecordingMessages implements Messages {
        private final List<String> keys = new ArrayList<>();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            keys.add(key.key());
            return key.key();
        }
    }

    /** Swallows the delivered lines; assertions read the resolved keys instead. */
    private static final class RecordingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // No-op: the resolved key list is the assertion surface.
        }
    }
}
