package com.uxplima.uxmessentials.moderation.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
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
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link PermissionSanctionBroadcast}: the staff sanction announcement reaches every
 * online player holding {@code uxmessentials.moderation.broadcast.receive} plus the console, and only them. A
 * player without the node is skipped; the console always gets the line. The {@link RunImmediately} scheduler
 * runs the global-thread fan-out inline so the deliveries are observable synchronously.
 */
class PermissionSanctionBroadcastTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String RECEIVE = "uxmessentials.moderation.broadcast.receive";

    private ServerMock server;
    private RunImmediately scheduler;
    private RecordingSink sink;
    private PermissionSanctionBroadcast broadcast;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        scheduler = new RunImmediately();
        sink = new RecordingSink();
        broadcast = new PermissionSanctionBroadcast(server, scheduler, new KeyMessages(), sink, "[uxm] ");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void announceReachesAReceiveHolderButNotAPlainPlayer() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(MockBukkit.createMockPlugin(), RECEIVE, true);
        PlayerMock bystander = server.addPlayer("Bystander");

        broadcast.announce(ModerationMessageKey.MOD_BROADCAST_BAN, placeholders());

        assertThat(sink.deliveredTo(staff.getUniqueId())).isTrue();
        assertThat(sink.deliveredTo(bystander.getUniqueId())).isFalse();
    }

    @Test
    void announceAlwaysWritesToTheConsole() {
        server.addPlayer("Staff").addAttachment(MockBukkit.createMockPlugin(), RECEIVE, true);

        broadcast.announce(ModerationMessageKey.MOD_BROADCAST_KICK, placeholders());

        org.mockbukkit.mockbukkit.command.MessageTarget console =
                (org.mockbukkit.mockbukkit.command.MessageTarget) server.getConsoleSender();
        assertThat(PLAIN.serialize(console.nextComponentMessage()))
                .contains(ModerationMessageKey.MOD_BROADCAST_KICK.key());
    }

    @Test
    void announceFansOutOnTheGlobalRegionThread() {
        server.addPlayer("Staff").addAttachment(MockBukkit.createMockPlugin(), RECEIVE, true);

        broadcast.announce(ModerationMessageKey.MOD_BROADCAST_MUTE, placeholders());

        assertThat(scheduler.globalTasks).isOne();
    }

    private static Map<String, String> placeholders() {
        return Map.of("actor", "Staff", "target", "Griefer", "reason", "x");
    }

    /** Runs every queued task inline so the fan-out and per-recipient delivery are observable synchronously. */
    private static final class RunImmediately implements Scheduler {
        private int globalTasks;

        @Override
        public void onGlobal(Runnable task) {
            globalTasks++;
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
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** Records which viewers were delivered to, so the perm-gated audience is assertable by UUID. */
    private static final class RecordingSink implements MessageSink {
        private final List<PlayerRef> viewers = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            viewers.add(viewer);
        }

        boolean deliveredTo(java.util.UUID uuid) {
            return viewers.stream().anyMatch(v -> v.uuid().equals(uuid));
        }
    }

    /** Echoes the catalog key as the rendered text so the console line is assertable. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
