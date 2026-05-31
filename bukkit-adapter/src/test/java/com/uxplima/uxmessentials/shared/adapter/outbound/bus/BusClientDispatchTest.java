package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.network.BalanceChanged;
import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.uxplima.uxmessentials.shared.network.VaultChanged;
import com.uxplima.uxmessentials.shared.network.WarpChanged;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Dispatch coverage of the backend bus client against the real (mock) Bukkit messenger as the transport. The
 * proxy is faked by feeding decoded-frame bytes straight into {@link BusClient#onPluginMessageReceived}, so the
 * test exercises exactly the two decisions the loop sentinel turns on:
 *
 * <ul>
 *   <li>a frame whose origin equals this backend's own {@code server-id} is dropped — never delivered to a
 *       listener — so a backend never re-applies its own mutation echoed back through the proxy;
 *   <li>a frame from a peer is delivered to every registered {@link RemoteSyncListener}, which is what
 *       invalidates the Caffeine cache so the next read sees the peer's change.
 * </ul>
 *
 * <p>The {@link Scheduler} is a synchronous inline fake so the off-tick dispatch the client routes through
 * runs in the test thread; a malformed frame is dropped without reaching a listener. The disabled-bus shape is
 * covered too: a client that was never started, and the {@link Bus#disabled} no-op publisher, both swallow a
 * publish so the single-server path stays a no-op.
 */
class BusClientDispatchTest {

    private static final String SELF = "survival-1";
    private static final String PEER = "lobby-2";
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private ServerMock server;
    private Plugin plugin;
    private InlineScheduler scheduler;
    private RecordingListener listener;
    private RemoteSyncRegistry registry;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        scheduler = new InlineScheduler();
        listener = new RecordingListener();
        registry = new RemoteSyncRegistry();
        registry.register(listener);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void appliesAFrameFromAPeer() {
        BusClient client = started();

        deliver(client, new HomeChanged(PEER, OWNER));

        assertThat(listener.applied).containsExactly(new HomeChanged(PEER, OWNER));
    }

    @Test
    void dropsAFrameThisBackendOriginated() {
        BusClient client = started();

        deliver(client, new HomeChanged(SELF, OWNER));

        assertThat(listener.applied).isEmpty();
    }

    @Test
    void deliversEveryMessageTypeFromAPeer() {
        BusClient client = started();

        deliver(client, new BalanceChanged(PEER, OWNER, "coins"));
        deliver(client, new WarpChanged(PEER, "spawn"));
        deliver(client, new VaultChanged(PEER, OWNER, 2));

        assertThat(listener.applied)
                .containsExactly(
                        new BalanceChanged(PEER, OWNER, "coins"),
                        new WarpChanged(PEER, "spawn"),
                        new VaultChanged(PEER, OWNER, 2));
    }

    @Test
    void dropsAMalformedFrameWithoutReachingAListener() {
        BusClient client = started();

        client.onPluginMessageReceived(channel(), carrier(), new byte[] {9, 9, 9});

        assertThat(listener.applied).isEmpty();
    }

    @Test
    void ignoresAFrameOnAnotherChannel() {
        BusClient client = started();

        client.onPluginMessageReceived(
                "other:channel", carrier(), NetworkMessageCodec.encode(new HomeChanged(PEER, OWNER)));

        assertThat(listener.applied).isEmpty();
    }

    @Test
    void aDisabledClientSwallowsAPublish() {
        BusClient client = new BusClient(plugin, scheduler, new SilentLogger(), disabledConfig(), registry);
        // start() is a no-op when disabled, so the client never runs and a publish is dropped silently.
        client.start();

        client.publish(new HomeChanged(SELF, OWNER));

        assertThat(scheduler.ran).isZero();
    }

    @Test
    void theDisabledBusPublisherDiscardsEveryFrame() {
        Bus bus = Bus.disabled(SELF);

        bus.publisher().publish(new HomeChanged(SELF, OWNER));

        assertThat(bus.publisher().serverId()).isEqualTo(SELF);
    }

    private BusClient started() {
        BusClient client = new BusClient(plugin, scheduler, new SilentLogger(), enabledConfig(), registry);
        client.start();
        return client;
    }

    private void deliver(BusClient client, NetworkMessage message) {
        client.onPluginMessageReceived(channel(), carrier(), NetworkMessageCodec.encode(message));
    }

    private static NetworkConfig enabledConfig() {
        return new NetworkConfig(true, SELF, channel(), 256);
    }

    private static NetworkConfig disabledConfig() {
        return new NetworkConfig(false, SELF, channel(), 256);
    }

    private static String channel() {
        return com.uxplima.uxmessentials.shared.network.BusChannel.FULL;
    }

    private Player carrier() {
        return server.addPlayer();
    }

    /** Records every frame the bus delivered, the stand-in for a context's cache-invalidation listener. */
    private static final class RecordingListener implements RemoteSyncListener {

        private final List<NetworkMessage> applied = new ArrayList<>();

        @Override
        public void onRemoteChange(NetworkMessage message) {
            applied.add(message);
        }
    }

    /** Runs every scheduled task inline so the off-tick dispatch fires in the test thread. */
    private static final class InlineScheduler implements Scheduler {

        private int ran;

        @Override
        public void onGlobal(Runnable task) {
            ran++;
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            ran++;
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            ran++;
            task.run();
        }

        @Override
        public void async(Runnable task) {
            ran++;
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            ran++;
            task.run();
        }
    }

    /** A logger that discards every line; the dispatch decisions are asserted, not the log output. */
    private static final class SilentLogger implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
