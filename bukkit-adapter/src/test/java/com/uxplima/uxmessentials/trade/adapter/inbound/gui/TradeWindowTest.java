package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.trade.application.TradeConfig;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the same-server trade window: two live views over one shared {@code TradeSession}. Placing a
 * stack re-reads the offer into the session and resets both confirmations (anti-scam); a change after one side has
 * confirmed un-confirms both; a both-confirm swaps the stacks between the two players; and every abort — a close — or a
 * commit into a full inventory returns or delivers every stack with none lost or duplicated.
 *
 * <p>The scheduler is a synchronous double so the entity-bound open, the live re-render, and the settlement all run
 * inline. A placement is driven by putting the stack in the window's editable slot and calling the same {@code
 * syncOffer} re-read the listener schedules after a click resolves — this keeps the test deterministic without
 * simulating Bukkit's mid-click slot mechanics.
 */
class TradeWindowTest {

    private ServerMock server;
    private Plugin plugin;
    private TradeLayout layout;
    private TradeSessions sessions;
    private TradeView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        TradeConfig config = new TradeConfig(true, List.of("coins"), List.of(), 0, 5, false, 12, 60, false);
        layout = new TradeLayout(config.slotsPerSide(), List.of());
        sessions = new TradeSessions();
        // Phase 2 coverage is items-only: no money prompt is driven and no economy is wired (null settlement); audit is
        // off in this config, so the no-op sink is never consulted.
        view = new TradeView(
                new KeyMessages(),
                new NoopSink(),
                new SyncScheduler(),
                config,
                sessions,
                (p, v, c, s, x) -> {},
                null,
                receipt -> {});
        server.getPluginManager().registerEvents(view.newListener(), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void placingAnItemUpdatesTheSessionAndResetsBothConfirms() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        view.open(alice, bob);

        place(alice, 0, new ItemStack(Material.DIAMOND, 3));

        TradeExchange exchange = exchangeOf(alice);
        assertThat(exchange.session().offer(TradeSide.INITIATOR).items()).hasSize(1);
        assertThat(exchange.confirmed(TradeSide.INITIATOR)).isFalse();
        assertThat(exchange.confirmed(TradeSide.PARTNER)).isFalse();
        // The counterpart's read-only mirror shows Alice's stack live.
        assertThat(bob.getOpenInventory().getTopInventory().getItem(layout.mirrorSlot(0)))
                .isNotNull();
    }

    @Test
    void aChangeAfterOneSideConfirmedUnconfirmsBoth() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        view.open(alice, bob);
        place(alice, 0, new ItemStack(Material.DIAMOND, 1));

        view.confirm(holder(alice));
        TradeExchange exchange = exchangeOf(alice);
        assertThat(exchange.confirmed(TradeSide.INITIATOR)).isTrue();

        // Bob changes his offer after Alice confirmed — the invariant clears BOTH confirmations.
        place(bob, 0, new ItemStack(Material.EMERALD, 1));

        assertThat(exchange.confirmed(TradeSide.INITIATOR)).isFalse();
        assertThat(exchange.confirmed(TradeSide.PARTNER)).isFalse();
    }

    @Test
    void bothConfirmSwapsItemsBetweenThePlayers() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        view.open(alice, bob);
        place(alice, 0, new ItemStack(Material.DIAMOND, 3));
        place(bob, 0, new ItemStack(Material.EMERALD, 2));

        view.confirm(holder(alice));
        view.confirm(holder(bob));

        assertThat(amount(alice, Material.EMERALD)).isEqualTo(2);
        assertThat(amount(bob, Material.DIAMOND)).isEqualTo(3);
        assertThat(amount(alice, Material.DIAMOND)).isZero();
        assertThat(amount(bob, Material.EMERALD)).isZero();
        assertThat(sessions.find(alice.getUniqueId())).isNull();
    }

    @Test
    void closingReturnsTheOfferedItemsToTheirOwner() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        view.open(alice, bob);
        place(alice, 0, new ItemStack(Material.DIAMOND, 5));

        // Alice closes her window; the listener cancels the trade and returns both sides' items.
        server.getPluginManager().callEvent(new InventoryCloseEvent(alice.getOpenInventory()));

        assertThat(amount(alice, Material.DIAMOND)).isEqualTo(5);
        assertThat(sessions.find(alice.getUniqueId())).isNull();
    }

    @Test
    void committingWithAFullRecipientDropsOverflowWithoutLoss() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        view.open(alice, bob);
        place(alice, 0, new ItemStack(Material.DIAMOND, 5));
        fillStorage(bob, new ItemStack(Material.STONE, 64));

        view.confirm(holder(alice));
        view.confirm(holder(bob));

        int inBobInventory = amount(bob, Material.DIAMOND);
        int onGround = server.getEntities().stream()
                .filter(Item.class::isInstance)
                .map(entity -> ((Item) entity).getItemStack())
                .filter(stack -> stack.getType() == Material.DIAMOND)
                .mapToInt(ItemStack::getAmount)
                .sum();
        assertThat(inBobInventory + onGround).isEqualTo(5); // none deleted
    }

    private void place(PlayerMock player, int slot, ItemStack stack) {
        TradeHolder holder = holder(player);
        holder.getInventory().setItem(layout.editableSlot(slot), stack);
        view.syncOffer(holder);
    }

    private TradeHolder holder(PlayerMock player) {
        return (TradeHolder) player.getOpenInventory().getTopInventory().getHolder();
    }

    private TradeExchange exchangeOf(PlayerMock player) {
        return java.util.Objects.requireNonNull(sessions.find(player.getUniqueId()));
    }

    private static int amount(PlayerMock player, Material material) {
        return Arrays.stream(player.getInventory().getContents())
                .filter(stack -> stack != null && stack.getType() == material)
                .mapToInt(ItemStack::getAmount)
                .sum();
    }

    private static void fillStorage(PlayerMock player, ItemStack filler) {
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, filler.clone());
        }
    }

    /** Resolves any key to its plain key string; the layout renders it as literal text. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Swallows delivery; the trade-window tests assert on items and session state, not on chat. */
    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    /** Runs every scheduled task inline so the open, re-render, and settlement complete in-test. */
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
