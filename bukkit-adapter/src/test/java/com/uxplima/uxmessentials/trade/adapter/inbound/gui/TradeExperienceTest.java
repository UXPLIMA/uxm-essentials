package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
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
import com.uxplima.uxmessentials.trade.application.TradeSettlement;
import com.uxplima.uxmessentials.trade.application.port.TradeEconomy;
import com.uxplima.uxmessentials.trade.application.port.TradeExperience;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of experience as a stakeable trade resource, the experience counterpart of the money coverage.
 * Experience is staked directly on the shared exchange (the seam the amount prompt drives) so the test needs no real
 * anvil. Staking experience clears both confirmations exactly as an item or money change does; a both-confirm moves each
 * side's staked experience to the other player; a staker who cannot cover their stake at settle blocks the whole trade
 * with nothing moved; and a cancel (window close) leaves the staker's experience untouched, since same-server
 * experience is only debited at commit.
 */
class TradeExperienceTest {

    private ServerMock server;
    private Plugin plugin;
    private TradeLayout layout;
    private TradeSessions sessions;
    private TradeView view;
    private RecordingExperience experience;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        TradeConfig config = new TradeConfig(true, List.of("coins"), List.of(), 0, 5, false, 20, 60, false);
        layout = new TradeLayout(config.slotsPerSide(), List.of());
        sessions = new TradeSessions();
        experience = new RecordingExperience();
        view = new TradeView(
                new KeyMessages(),
                new NoopSink(),
                new SyncScheduler(),
                config,
                sessions,
                (p, v, c, s, x) -> {},
                (p, v, s, x) -> {},
                new TradeSettlement(new NoopEconomy(), experience),
                experience,
                false,
                receipt -> {});
        server.getPluginManager().registerEvents(view.newListener(), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void stakingExperienceResetsBothConfirmations() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        view.open(alice, bob);
        place(alice, 0, new ItemStack(Material.DIAMOND, 1));

        view.confirm(holder(alice));
        assertThat(exchangeOf(alice).confirmed(TradeSide.INITIATOR)).isTrue();

        // Bob stakes experience after Alice confirmed; the anti-scam invariant clears BOTH confirmations.
        exchangeOf(alice).setExperience(TradeSide.PARTNER, 50L);

        assertThat(exchangeOf(alice).confirmed(TradeSide.INITIATOR)).isFalse();
        assertThat(exchangeOf(alice).confirmed(TradeSide.PARTNER)).isFalse();
    }

    @Test
    void bothConfirmMovesStakedExperienceBothWays() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        experience.set(alice.getUniqueId(), 500L);
        experience.set(bob.getUniqueId(), 500L);
        view.open(alice, bob);
        exchangeOf(alice).setExperience(TradeSide.INITIATOR, 100L);
        exchangeOf(alice).setExperience(TradeSide.PARTNER, 40L);

        view.confirm(holder(alice));
        view.confirm(holder(bob));

        // Alice gave 100 and received Bob's 40; Bob gave 40 and received Alice's 100.
        assertThat(experience.balance(alice.getUniqueId())).isEqualTo(440L);
        assertThat(experience.balance(bob.getUniqueId())).isEqualTo(560L);
        assertThat(sessions.find(alice.getUniqueId())).isNull();
    }

    @Test
    void aStakerWhoCannotAffordTheirExperienceBlocksTheTradeWithNothingMoved() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        experience.set(alice.getUniqueId(), 50L); // not enough for the 100 staked below
        experience.set(bob.getUniqueId(), 500L);
        view.open(alice, bob);
        place(alice, 0, new ItemStack(Material.DIAMOND, 1));
        place(bob, 0, new ItemStack(Material.EMERALD, 1));
        exchangeOf(alice).setExperience(TradeSide.INITIATOR, 100L);

        view.confirm(holder(alice));
        view.confirm(holder(bob));

        // No experience moved, and the items came back to their owners rather than swapping.
        assertThat(experience.balance(alice.getUniqueId())).isEqualTo(50L);
        assertThat(experience.balance(bob.getUniqueId())).isEqualTo(500L);
        assertThat(amount(alice, Material.DIAMOND)).isEqualTo(1);
        assertThat(amount(bob, Material.EMERALD)).isEqualTo(1);
        assertThat(amount(alice, Material.EMERALD)).isZero();
        assertThat(sessions.find(alice.getUniqueId())).isNull();
    }

    @Test
    void cancellingLeavesStakedExperienceWithTheStaker() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        experience.set(alice.getUniqueId(), 500L);
        view.open(alice, bob);
        exchangeOf(alice).setExperience(TradeSide.INITIATOR, 100L);

        // Alice closes her window before the swap; same-server experience is only debited at commit, so hers is intact.
        server.getPluginManager().callEvent(new InventoryCloseEvent(alice.getOpenInventory()));

        assertThat(experience.balance(alice.getUniqueId())).isEqualTo(500L);
        assertThat(sessions.find(alice.getUniqueId())).isNull();
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

    /** A fake experience seam over an in-memory per-player balance: a guarded withdraw and an unconditional deposit. */
    private static final class RecordingExperience implements TradeExperience {
        private final Map<UUID, Long> balances = new HashMap<>();

        void set(UUID who, long amount) {
            balances.put(who, amount);
        }

        long balance(UUID who) {
            return balances.getOrDefault(who, 0L);
        }

        @Override
        public long available(PlayerRef who) {
            return balance(who.uuid());
        }

        @Override
        public boolean withdraw(PlayerRef who, long points) {
            if (balance(who.uuid()) < points) {
                return false;
            }
            balances.put(who.uuid(), balance(who.uuid()) - points);
            return true;
        }

        @Override
        public void deposit(PlayerRef who, long points) {
            balances.put(who.uuid(), balance(who.uuid()) + points);
        }
    }

    /** No money is staked in these experience tests, so the economy seam is a permissive stub. */
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

    /** Resolves any key to its plain key string. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Swallows delivery; these tests assert on experience balances and session state, not on chat. */
    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    /** Runs every scheduled task inline so the open, settlement, and delivery complete in-test. */
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
