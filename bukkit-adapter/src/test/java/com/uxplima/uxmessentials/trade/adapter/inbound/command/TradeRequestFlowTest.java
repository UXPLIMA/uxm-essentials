package com.uxplima.uxmessentials.trade.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeSessions;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeView;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeWindows;
import com.uxplima.uxmessentials.trade.application.TradeConfig;
import com.uxplima.uxmessentials.trade.application.TradeCooldown;
import com.uxplima.uxmessentials.trade.application.TradeRequests;
import com.uxplima.uxmessentials.trade.application.TradeSettlement;
import com.uxplima.uxmessentials.trade.application.port.TradeEconomy;
import com.uxplima.uxmessentials.trade.application.port.TradeExperience;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /trade} request flow driven through the command's resolved-player handlers: a
 * request then an accept opens a live session; a self-request, an offline requester at accept, an out-of-range target,
 * and a cooling-down sender are all refused (nothing is staged and no window opens); and a deny clears the pending
 * request. The scheduler is synchronous so the accept's window open settles inline, and the clock is hand-advanced so
 * the cooldown boundary is deterministic.
 */
class TradeRequestFlowTest {

    private ServerMock server;
    private Plugin plugin;
    private TradeSessions sessions;
    private TradeView view;
    private MutableClock clock;
    private TradeRequests requests;
    private TradeCooldown cooldown;
    private TradeCommand command;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        sessions = new TradeSessions();
        TradeConfig viewConfig = config(0, 0);
        TradeExperience experience = new NoopExperience();
        KeyMessages messages = new KeyMessages();
        TestMenuEngine engine = TestMenuEngine.create(messages, new SyncScheduler());
        view = new TradeView(
                messages,
                new NoopSink(),
                new SyncScheduler(),
                viewConfig,
                sessions,
                TradeWindows.sameServer(messages, engine.menus(), List.of()),
                (p, v, c, s, x) -> {},
                (p, v, s, x) -> {},
                new TradeSettlement(new NoopEconomy(), experience),
                experience,
                receipt -> {});
        view.register(engine.bindings());
        engine.installListener(plugin);
        server.getPluginManager().registerEvents(view.newListener(), plugin);
        // A default wiring so the request/cooldown fields are always initialised; each test re-wires with its own
        // config (distance, cooldown) as needed.
        wire(viewConfig);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aRequestThenAnAcceptOpensASession() {
        wire(config(0, 0));
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");

        command.send(alice, bob);
        assertThat(requests.hasPending(alice.getUniqueId(), bob.getUniqueId())).isTrue();

        command.accept(bob, null);

        assertThat(sessions.isTrading(alice.getUniqueId())).isTrue();
        assertThat(sessions.isTrading(bob.getUniqueId())).isTrue();
        // The request was consumed on accept.
        assertThat(requests.hasPending(alice.getUniqueId(), bob.getUniqueId())).isFalse();
    }

    @Test
    void aSelfRequestIsRefused() {
        wire(config(0, 0));
        PlayerMock alice = server.addPlayer("Alice");

        command.send(alice, alice);

        assertThat(requests.hasPending(alice.getUniqueId(), alice.getUniqueId()))
                .isFalse();
        assertThat(sessions.isTrading(alice.getUniqueId())).isFalse();
    }

    @Test
    void anAcceptOfAnOfflineRequesterOpensNothing() {
        wire(config(0, 0));
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        command.send(alice, bob);

        alice.disconnect();
        command.accept(bob, null);

        assertThat(sessions.isTrading(bob.getUniqueId())).isFalse();
    }

    @Test
    void aDistanceLimitRefusesAnOutOfRangeTargetAndAllowsANearOne() {
        wire(config(5, 0));
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");

        bob.teleport(new Location(alice.getWorld(), 200, 64, 200));
        command.send(alice, bob);
        assertThat(requests.hasPending(alice.getUniqueId(), bob.getUniqueId())).isFalse();

        bob.teleport(alice.getLocation());
        command.send(alice, bob);
        assertThat(requests.hasPending(alice.getUniqueId(), bob.getUniqueId())).isTrue();
    }

    @Test
    void theCooldownBlocksASecondRequestUntilItLapses() {
        wire(config(0, 5));
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");

        command.send(alice, bob);
        assertThat(requests.hasPending(alice.getUniqueId(), bob.getUniqueId())).isTrue();
        // Consume the pending request so only the cooldown can block the retry.
        requests.resolve(bob.getUniqueId(), null);

        command.send(alice, bob);
        assertThat(requests.hasPending(alice.getUniqueId(), bob.getUniqueId())).isFalse();

        clock.advance(Duration.ofSeconds(6));
        command.send(alice, bob);
        assertThat(requests.hasPending(alice.getUniqueId(), bob.getUniqueId())).isTrue();
    }

    @Test
    void aDenyClearsThePendingRequestAndOpensNothing() {
        wire(config(0, 0));
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        command.send(alice, bob);

        command.deny(bob, null);

        assertThat(requests.hasPending(alice.getUniqueId(), bob.getUniqueId())).isFalse();
        assertThat(sessions.isTrading(bob.getUniqueId())).isFalse();
    }

    private void wire(TradeConfig config) {
        clock = new MutableClock(Instant.EPOCH);
        requests = new TradeRequests(clock, Duration.ofSeconds(config.requestExpirySeconds()));
        cooldown = new TradeCooldown(clock, Duration.ofSeconds(config.cooldownSeconds()));
        command = new TradeCommand(requests, cooldown, config, sessions, view::open, new KeyMessages(), null);
    }

    private static TradeConfig config(int distance, int cooldownSeconds) {
        return new TradeConfig(true, List.of("coins"), List.of(), distance, cooldownSeconds, false, 60, false);
    }

    /** Resolves any key to its plain key string; these tests assert on registry and session state, not on chat. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Swallows delivery. */
    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    /** The request flow stages no money or experience, so the settlement's seams are permissive stubs. */
    private static final class NoopEconomy implements TradeEconomy {
        @Override
        public boolean canAfford(PlayerRef who, java.math.BigDecimal amount, String currencyId) {
            return true;
        }

        @Override
        public boolean transfer(PlayerRef from, PlayerRef to, java.math.BigDecimal amount, String currencyId) {
            return true;
        }

        @Override
        public boolean withdraw(PlayerRef who, java.math.BigDecimal amount, String currencyId) {
            return true;
        }

        @Override
        public void deposit(PlayerRef who, java.math.BigDecimal amount, String currencyId) {}
    }

    private static final class NoopExperience implements TradeExperience {
        @Override
        public long available(PlayerRef who) {
            return 0L;
        }

        @Override
        public boolean withdraw(PlayerRef who, long points) {
            return true;
        }

        @Override
        public void deposit(PlayerRef who, long points) {}
    }

    /** Runs every scheduled task inline so the accept's window open completes in-test. */
    private static final class SyncScheduler implements Scheduler {
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

    /** A hand-advanced {@link Clock} so the cooldown boundary is deterministic. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration delta) {
            now = now.plus(delta);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
