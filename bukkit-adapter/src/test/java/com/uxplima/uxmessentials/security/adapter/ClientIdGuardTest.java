package com.uxplima.uxmessentials.security.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.domain.ClientIdMode;
import com.uxplima.uxmessentials.security.domain.ClientVerdict;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
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
 * MockBukkit coverage of the client-brand guard: a block-listed brand is denied (kicked) and flagged to staff, an
 * allow-listed brand is admitted, flag mode records and flags without ever kicking, and a disabled guard is a
 * no-op. The brand is fed to {@code evaluate} directly (MockBukkit does not implement the brand channel), which is
 * exactly the decision the join listener drives.
 */
class ClientIdGuardTest {

    private ServerMock server;
    private ClientBrandRegistry registry;
    private RecordingSink sink;
    private SecurityStaffNotifier notifier;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        registry = new ClientBrandRegistry();
        sink = new RecordingSink();
        notifier = new SecurityStaffNotifier(server, new InlineScheduler(), new KeyMessages(), sink, new NoopLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aBlockListedBrandIsDeniedAndFlaggedToStaff() {
        PlayerMock joiner = server.addPlayer();
        staffWatcher();
        ClientGuard guard = guard(config(true, ClientIdMode.BLOCK_LIST, List.of("wurst")));

        ClientVerdict verdict = guard.evaluate(joiner, BukkitRefs.toRef(joiner), "wurst");

        assertThat(verdict.allowed()).isFalse();
        assertThat(joiner.isOnline()).isFalse();
        assertThat(registry.brandOf(joiner.getUniqueId())).contains("wurst");
        assertThat(sink.delivered).contains("security.client.flagged");
    }

    @Test
    void anAllowListedBrandIsAdmitted() {
        PlayerMock joiner = server.addPlayer();
        ClientGuard guard = guard(config(true, ClientIdMode.ALLOW_LIST, List.of("vanilla")));

        ClientVerdict verdict = guard.evaluate(joiner, BukkitRefs.toRef(joiner), "vanilla");

        assertThat(verdict.allowed()).isTrue();
        assertThat(joiner.isOnline()).isTrue();
        assertThat(sink.delivered).isEmpty();
    }

    @Test
    void flagModeRecordsAndFlagsWithoutKicking() {
        PlayerMock joiner = server.addPlayer();
        staffWatcher();
        ClientGuard guard = guard(config(true, ClientIdMode.FLAG, List.of("wurst")));

        ClientVerdict verdict = guard.evaluate(joiner, BukkitRefs.toRef(joiner), "wurst");

        assertThat(verdict).isEqualTo(new ClientVerdict(true, true));
        assertThat(joiner.isOnline()).isTrue();
        assertThat(registry.brandOf(joiner.getUniqueId())).contains("wurst");
        assertThat(sink.delivered).contains("security.client.flagged");
    }

    @Test
    void aDisabledGuardReadsNoBrandAndKicksNobody() {
        PlayerMock joiner = server.addPlayer();

        // onJoin short-circuits on the disabled flag before it ever reads the brand channel.
        guard(config(false, ClientIdMode.BLOCK_LIST, List.of("wurst"))).onJoin(joiner);

        assertThat(registry.brandOf(joiner.getUniqueId())).isEmpty();
        assertThat(joiner.isOnline()).isTrue();
    }

    private void staffWatcher() {
        PlayerMock staff = server.addPlayer();
        staff.addAttachment(MockBukkit.createMockPlugin("Notify"), SecurityStaffNotifier.NOTIFY_NODE, true);
    }

    private ClientGuard guard(SecurityConfig.ClientId config) {
        return new ClientGuard(registry, config, notifier, new InlineScheduler(), new KeyMessages());
    }

    private static SecurityConfig.ClientId config(boolean enabled, ClientIdMode mode, List<String> brands) {
        return new SecurityConfig.ClientId(enabled, mode, brands);
    }

    /** Resolves every key to its dotted catalog id so a test can assert which message was delivered. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Records every delivered message so a test can assert staff saw the expected line. */
    private static final class RecordingSink implements MessageSink {
        private final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** Runs every scheduler hop inline so the kick and staff fan-out resolve synchronously in the test. */
    private static final class InlineScheduler implements Scheduler {
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
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
