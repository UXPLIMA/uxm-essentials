package com.uxplima.uxmessentials.trade.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.api.view.UxmTrade;
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
 * The published trade query over a real window: a trade that is open is reported for both of its participants,
 * with the two of them in the roles the trade gave them, and a trade that has ended is reported for neither.
 */
class TradeQueriesTest {

    private ServerMock server;
    private TradeSessions sessions;
    private TradeView view;
    private TradeQueries queries;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        sessions = new TradeSessions();
        KeyMessages messages = new KeyMessages();
        TestMenuEngine engine = TestMenuEngine.create(messages, new SyncScheduler());
        TradeExperience experience = new NoopExperience();
        view = new TradeView(
                messages,
                new NoopSink(),
                new SyncScheduler(),
                new TradeConfig(true, List.of(), List.of(), 0, 5, false, 60, false),
                sessions,
                TradeWindows.sameServer(messages, engine.menus(), List.of()),
                (p, v, c, s, x) -> {},
                (p, v, s, x) -> {},
                new TradeSettlement(new NoopEconomy(), experience),
                experience,
                receipt -> {},
                event -> {});
        view.register(engine.bindings());
        engine.installListener(plugin);
        server.getPluginManager().registerEvents(view.newListener(), plugin);
        queries = new TradeQueries(sessions);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anOpenTradeIsReportedForBothParticipants() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");

        view.open(alice, bob);

        assertThat(queries.isTrading(alice.getUniqueId())).isTrue();
        assertThat(queries.isTrading(bob.getUniqueId())).isTrue();
        assertThat(queries.of(alice.getUniqueId())).isEqualTo(queries.of(bob.getUniqueId()));
    }

    @Test
    void theTwoSidesKeepTheRolesTheTradeGaveThem() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");

        view.open(alice, bob);
        UxmTrade trade = queries.of(bob.getUniqueId()).orElseThrow();

        assertThat(trade.initiatorId()).isEqualTo(alice.getUniqueId());
        assertThat(trade.initiatorName()).isEqualTo("Alice");
        assertThat(trade.partnerId()).isEqualTo(bob.getUniqueId());
        assertThat(trade.partnerName()).isEqualTo("Bob");
        assertThat(trade.initiatorConfirmed()).isFalse();
        assertThat(trade.partnerConfirmed()).isFalse();
        assertThat(trade.bothConfirmed()).isFalse();
    }

    @Test
    void somebodyWhoIsNotTradingIsAnAnswerRatherThanAnAbsence() {
        assertThat(queries.isTrading(UUID.randomUUID())).isFalse();
        assertThat(queries.of(UUID.randomUUID())).isEmpty();
    }

    @Test
    void everyOpenTradeIsListedOnceAndAnEndedOneIsNotListedAtAll() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        PlayerMock cara = server.addPlayer("Cara");
        PlayerMock dan = server.addPlayer("Dan");
        view.open(alice, bob);
        view.open(cara, dan);

        assertThat(queries.open()).hasSize(2);

        view.closeAll();

        assertThat(queries.open()).isEmpty();
        assertThat(queries.isTrading(alice.getUniqueId())).isFalse();
    }

    /** Resolves any key to its plain key string. */
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

    /** No money is staked here, so the economy seam is a permissive stub. */
    private static final class NoopEconomy implements TradeEconomy {
        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return true;
        }

        @Override
        public boolean transfer(PlayerRef from, PlayerRef to, BigDecimal amount, String currencyId) {
            return true;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
            return true;
        }

        @Override
        public void deposit(PlayerRef who, BigDecimal amount, String currencyId) {}
    }

    /** No experience is staked here either. */
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

    /** Runs every scheduled task inline, so an open and a drain both finish inside the test. */
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
        public void onEntity(PlayerRef player, Runnable task, Runnable gone) {
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
