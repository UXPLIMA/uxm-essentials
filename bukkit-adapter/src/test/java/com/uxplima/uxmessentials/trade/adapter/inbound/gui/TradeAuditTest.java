package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.trade.application.TradeConfig;
import com.uxplima.uxmessentials.trade.application.TradeReceipt;
import com.uxplima.uxmessentials.trade.application.port.TradeAudit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the completed-trade audit: a both-confirm swap emits exactly one {@link TradeReceipt} — the
 * two participants and each side's item quantity — when the module's {@code audit} knob is on, and emits nothing when it
 * is off. The scheduler is synchronous so the settlement (and its audit emission) runs inline.
 */
class TradeAuditTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aCompletedTradeEmitsOneReceiptWhenAuditIsOn() {
        Fixture fixture = fixture(true);
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        fixture.view.open(alice, bob);
        place(fixture, alice, new ItemStack(Material.DIAMOND, 3));
        place(fixture, bob, new ItemStack(Material.EMERALD, 2));

        fixture.view.confirm(holder(alice));
        fixture.view.confirm(holder(bob));

        assertThat(fixture.audit.receipts).hasSize(1);
        TradeReceipt receipt = fixture.audit.receipts.get(0);
        assertThat(receipt.initiator().name()).isEqualTo("Alice");
        assertThat(receipt.partner().name()).isEqualTo("Bob");
        assertThat(receipt.initiatorItems()).isEqualTo(3);
        assertThat(receipt.partnerItems()).isEqualTo(2);
    }

    @Test
    void aCompletedTradeIsSilentWhenAuditIsOff() {
        Fixture fixture = fixture(false);
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        fixture.view.open(alice, bob);
        place(fixture, alice, new ItemStack(Material.DIAMOND, 3));
        place(fixture, bob, new ItemStack(Material.EMERALD, 2));

        fixture.view.confirm(holder(alice));
        fixture.view.confirm(holder(bob));

        // The swap still ran…
        assertThat(fixture.sessions.isTrading(alice.getUniqueId())).isFalse();
        // …but no audit line was emitted.
        assertThat(fixture.audit.receipts).isEmpty();
    }

    private Fixture fixture(boolean auditEnabled) {
        TradeSessions sessions = new TradeSessions();
        RecordingAudit audit = new RecordingAudit();
        TradeConfig config = new TradeConfig(true, List.of("coins"), List.of(), 0, 5, false, 12, 60, auditEnabled);
        TradeLayout layout = new TradeLayout(config.slotsPerSide(), List.of());
        TradeView view = new TradeView(
                new KeyMessages(),
                new NoopSink(),
                new SyncScheduler(),
                config,
                sessions,
                (p, v, c, s, x) -> {},
                null,
                audit);
        server.getPluginManager().registerEvents(view.newListener(), plugin);
        return new Fixture(sessions, layout, view, audit);
    }

    private void place(Fixture fixture, PlayerMock player, ItemStack stack) {
        TradeHolder holder = holder(player);
        holder.getInventory().setItem(fixture.layout.editableSlot(0), stack);
        fixture.view.syncOffer(holder);
    }

    private TradeHolder holder(PlayerMock player) {
        return (TradeHolder) player.getOpenInventory().getTopInventory().getHolder();
    }

    /** One test's collaborators over a shared session — kept local so each test picks its own audit setting. */
    private record Fixture(TradeSessions sessions, TradeLayout layout, TradeView view, RecordingAudit audit) {}

    /** Captures every completed-trade receipt so the test can assert emission (or silence). */
    private static final class RecordingAudit implements TradeAudit {
        private final List<TradeReceipt> receipts = new ArrayList<>();

        @Override
        public void completed(TradeReceipt receipt) {
            receipts.add(receipt);
        }
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

    /** Runs every scheduled task inline so the settlement and its audit emission complete in-test. */
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
}
