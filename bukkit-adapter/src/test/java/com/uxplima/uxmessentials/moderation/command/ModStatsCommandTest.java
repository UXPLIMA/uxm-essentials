package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
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
import com.uxplima.uxmessentials.moderation.adapter.inbound.command.ModStatsCommand;
import com.uxplima.uxmessentials.moderation.application.PunishmentStats;
import com.uxplima.uxmessentials.moderation.application.ReviewPunishmentStats;
import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.application.port.TargetResolver;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
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
 * MockBukkit coverage of {@code /modstats}. The {@link ReviewPunishmentStats} use case is built over an
 * in-memory {@link FakeHistory} and a fixed clock and exposed through a mocked {@link ModerationServices}, so a
 * dispatch is observed by the rendered reply keys the {@link RecordingMessages} captures. The read is hopped off
 * the tick thread through the {@link Scheduler} port, which {@link RunInline} runs synchronously. It proves the
 * bare form renders the server-wide leaderboard, a numeric first argument routes to the server-wide window
 * ({@code /modstats 7}), a name routes to the per-staff breakdown, the timed forms pick the windowed keys, an
 * unknown staff name is rejected before any read, and the permission predicate blocks an unprivileged sender.
 */
class ModStatsCommandTest {

    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");
    private static final UUID MOD = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    private ServerMock server;
    private RunInline scheduler;
    private RecordingMessages messages;
    private RecordingSink sink;
    private FakeHistory history;
    private FakeTargets targets;
    private ModerationServices services;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        scheduler = new RunInline();
        messages = new RecordingMessages();
        sink = new RecordingSink();
        history = new FakeHistory();
        targets = new FakeTargets();
        Notifier notifier = new Notifier(messages, sink);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ReviewPunishmentStats review = new ReviewPunishmentStats(history, new PunishmentStats(), notifier, clock);
        services = mock(ModerationServices.class);
        lenient().when(services.targets()).thenReturn(targets);
        lenient().when(services.reviewPunishmentStats()).thenReturn(review);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void serverWideRendersTheLeaderboard() {
        history.add(entry(staff("Busy"), SanctionAction.BAN, NOW.minus(Duration.ofDays(1))));
        history.add(entry(staff("Quiet"), SanctionAction.WARN, NOW.minus(Duration.ofDays(1))));

        dispatch(command(), staff(), "modstats");

        assertThat(messages.keys).contains("moderation.stats.header", "moderation.stats.entry");
        assertThat(scheduler.asyncTasks).isOne();
    }

    @Test
    void aNumericFirstArgumentIsReadAsAServerWideWindow() {
        history.add(entry(staff("Busy"), SanctionAction.BAN, NOW.minus(Duration.ofDays(1))));

        dispatch(command(), staff(), "modstats 7");

        assertThat(messages.keys).contains("moderation.stats.header-window");
        assertThat(messages.keys).doesNotContain("moderation.unknown-target");
    }

    @Test
    void aNameIsReadAsThePerStaffBreakdown() {
        targets.add(new PlayerRef(MOD, "Mod"));
        history.add(entry(Issuer.stored(Optional.of(MOD), "Mod"), SanctionAction.KICK, NOW.minus(Duration.ofDays(2))));

        dispatch(command(), staff(), "modstats Mod");

        assertThat(messages.keys).containsExactly("moderation.stats.staff");
    }

    @Test
    void aTimedPerStaffFormPicksTheWindowedKey() {
        targets.add(new PlayerRef(MOD, "Mod"));
        history.add(entry(Issuer.stored(Optional.of(MOD), "Mod"), SanctionAction.BAN, NOW.minus(Duration.ofDays(1))));

        dispatch(command(), staff(), "modstats Mod 3");

        assertThat(messages.keys).containsExactly("moderation.stats.staff-window");
    }

    @Test
    void anUnknownStaffNameIsRejectedBeforeAnyRead() {
        dispatch(command(), staff(), "modstats Ghost");

        assertThat(messages.keys).containsExactly("moderation.unknown-target");
        assertThat(scheduler.asyncTasks).isZero();
    }

    @Test
    void isBlockedWithoutThePermission() {
        history.add(entry(staff("Busy"), SanctionAction.BAN, NOW.minus(Duration.ofDays(1))));

        dispatch(command(), unprivileged(), "modstats");

        assertThat(scheduler.asyncTasks).isZero();
        assertThat(messages.keys).isEmpty();
    }

    private ModStatsCommand command() {
        return new ModStatsCommand(services, messages, sink, scheduler);
    }

    private static Issuer staff(String name) {
        return Issuer.stored(Optional.of(UUID.randomUUID()), name);
    }

    private static SanctionHistoryEntry entry(Issuer actor, SanctionAction action, Instant at) {
        return new SanctionHistoryEntry(
                action, UUID.randomUUID(), actor, Optional.empty(), at, Optional.empty(), Optional.empty());
    }

    private PlayerMock staff() {
        PlayerMock actor = server.addPlayer("Operator");
        actor.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.moderation.stats", true);
        return actor;
    }

    private PlayerMock unprivileged() {
        return server.addPlayer("Visitor");
    }

    private void dispatch(ModStatsCommand command, PlayerMock sender, String input) {
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

    /** An append-only in-memory history: {@code allSince} and {@code recentByActor} filter and read newest-first. */
    private static final class FakeHistory implements SanctionHistory {
        private final List<SanctionHistoryEntry> rows = new ArrayList<>();

        void add(SanctionHistoryEntry entry) {
            rows.add(entry);
        }

        @Override
        public void append(SanctionHistoryEntry entry) {
            rows.add(entry);
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
            return List.of();
        }

        @Override
        public List<SanctionHistoryEntry> recentByActor(UUID actor, int limit) {
            return newestFirst(rows.stream()
                    .filter(e -> e.actor().uuid().filter(actor::equals).isPresent())
                    .toList());
        }

        @Override
        public List<SanctionHistoryEntry> allSince(Instant threshold, int limit) {
            return newestFirst(
                    rows.stream().filter(e -> !e.at().isBefore(threshold)).toList());
        }

        private static List<SanctionHistoryEntry> newestFirst(List<SanctionHistoryEntry> matches) {
            List<SanctionHistoryEntry> ordered = new ArrayList<>(matches);
            Collections.reverse(ordered);
            return List.copyOf(ordered);
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

    /** Records the keys resolved so a dispatch's rendered replies are assertable by key. */
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
