package com.uxplima.uxmessentials.npc.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.npc.adapter.inbound.listener.NpcCommandRunner;
import com.uxplima.uxmessentials.npc.application.port.NpcEconomy;
import com.uxplima.uxmessentials.npc.domain.ClickTrigger;
import com.uxplima.uxmessentials.npc.domain.NpcAction;
import com.uxplima.uxmessentials.npc.domain.NpcActionType;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
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
 * Covers {@link BukkitNpcActionRunner} as a sequenced executor with gates and a delay continuation. The first
 * suite pins the original behaviour (trigger filtering, ordered command dispatch, fail-soft); the rest pin the
 * richer N12 action types: a {@code DELAY} schedules the tail and aborts on a viewer who logged off; the
 * {@code CHANCE}/{@code PERMISSION}/{@code CONDITION}/{@code COST} gates abort the remaining chain on a failed
 * verdict (a malformed gate spec is skipped, never aborting); and {@code GIVE} delivers an item with overflow
 * dropping.
 */
class BukkitNpcActionRunnerTest {

    private ServerMock server;
    private PlayerMock player;
    private RecordingRunner commandRunner;
    private RecordingConnector connector;
    private CapturingScheduler scheduler;
    private FakePermissions permissions;
    private RecordingEconomy economy;
    private RecordingMessages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        player = server.addPlayer("Steve");
        commandRunner = new RecordingRunner();
        connector = new RecordingConnector();
        scheduler = new CapturingScheduler();
        permissions = new FakePermissions();
        economy = new RecordingEconomy();
        messages = new RecordingMessages();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private BukkitNpcActionRunner runnerWith(Optional<NpcEconomy> eco) {
        return new BukkitNpcActionRunner(
                commandRunner, connector, scheduler, permissions, eco, messages, new NoopLogger());
    }

    private BukkitNpcActionRunner runner() {
        return runnerWith(Optional.of(economy));
    }

    // --- original behaviour ---------------------------------------------------------------------------------

    @Test
    void runsOnlyTheActionsWhoseTriggerMatchesARightClick() {
        runner().run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.RIGHT_CLICK, NpcActionType.RUN_PLAYER, "right"),
                                new NpcAction(ClickTrigger.LEFT_CLICK, NpcActionType.RUN_PLAYER, "left"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "any")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("right", "any");
    }

    @Test
    void runsOnlyTheActionsWhoseTriggerMatchesAnAttack() {
        runner().run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.RIGHT_CLICK, NpcActionType.RUN_PLAYER, "right"),
                                new NpcAction(ClickTrigger.LEFT_CLICK, NpcActionType.RUN_PLAYER, "left"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "any")),
                        true);

        assertThat(commandRunner.playerCommands).containsExactly("left", "any");
    }

    @Test
    void dispatchesConsoleAndPlayerCommandsSubstitutingThePlayerToken() {
        runner().run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_CONSOLE, "give {player} diamond"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "msg {player} hi")),
                        false);

        assertThat(commandRunner.consoleCommands).containsExactly("give Steve diamond");
        assertThat(commandRunner.playerCommands).containsExactly("msg Steve hi");
    }

    @Test
    void sendsAConnectRequestForAConnectAction() {
        runner().run(player, List.of(new NpcAction(ClickTrigger.ANY, NpcActionType.CONNECT, "lobby")), false);

        assertThat(connector.servers).containsExactly("lobby");
    }

    @Test
    void oneMalformedEffectMidChainIsSkippedAndTheRestStillRun() {
        runner().run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "first"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.SOUND, "not.a.real.sound.key.at.all"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "third")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("first", "third");
    }

    // --- DELAY ----------------------------------------------------------------------------------------------

    @Test
    void delaySchedulesTheTailAndRunningItContinuesTheChain() {
        runner().run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "before"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.DELAY, "40"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "after")),
                        false);

        // The pre-delay action ran inline; the tail is parked on the scheduler, not yet run.
        assertThat(commandRunner.playerCommands).containsExactly("before");
        assertThat(scheduler.pendingDelays()).isEqualTo(1);

        scheduler.runAllDelayed();
        assertThat(commandRunner.playerCommands).containsExactly("before", "after");
    }

    @Test
    void delayAbortsWhenTheViewerLoggedOffDuringTheWait() {
        runner().run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "before"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.DELAY, "40"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "after")),
                        false);

        scheduler.disconnect(player); // the viewer logs off while the tail is parked
        scheduler.runAllDelayed();

        assertThat(commandRunner.playerCommands).containsExactly("before");
    }

    // --- CHANCE ---------------------------------------------------------------------------------------------

    @Test
    void chanceOfOneHundredAlwaysContinues() {
        runner().run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.ANY, NpcActionType.CHANCE, "100"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "reward")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("reward");
    }

    @Test
    void chanceOfZeroAlwaysAbortsTheRest() {
        runner().run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "always"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.CHANCE, "0"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "reward")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("always");
    }

    // --- PERMISSION -----------------------------------------------------------------------------------------

    @Test
    void permissionGateContinuesWhenHeld() {
        permissions.grant(player, "npc.vip");
        runner().run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.ANY, NpcActionType.PERMISSION, "npc.vip"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "vip")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("vip");
    }

    @Test
    void permissionGateAbortsWhenLacked() {
        runner().run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.ANY, NpcActionType.PERMISSION, "npc.vip"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "vip")),
                        false);

        assertThat(commandRunner.playerCommands).isEmpty();
    }

    // --- CONDITION ------------------------------------------------------------------------------------------

    @Test
    void conditionTrueContinues() {
        runner().run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.ANY, NpcActionType.CONDITION, "5 > 3"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "ok")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("ok");
    }

    @Test
    void conditionFalseAborts() {
        runner().run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.ANY, NpcActionType.CONDITION, "1 > 3"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "no")),
                        false);

        assertThat(commandRunner.playerCommands).isEmpty();
    }

    @Test
    void conditionDoesStringEqualityWhenNotNumeric() {
        runner().run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.ANY, NpcActionType.CONDITION, "alpha == alpha"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "same"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.CONDITION, "alpha == beta"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "unreached")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("same");
    }

    @Test
    void malformedConditionSkipsTheGateAndContinues() {
        runner().run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.ANY, NpcActionType.CONDITION, "garbage with no operator"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "still-runs")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("still-runs");
    }

    // --- COST -----------------------------------------------------------------------------------------------

    @Test
    void costDebitsOnceAndContinuesWhenAffordable() {
        economy.affordable = true;
        runner().run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.ANY, NpcActionType.COST, "50"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "bought")),
                        false);

        assertThat(economy.withdrawals).containsExactly(new BigDecimal("50"));
        assertThat(commandRunner.playerCommands).containsExactly("bought");
    }

    @Test
    void costAbortsAndMessagesWhenUnaffordable() {
        economy.affordable = false;
        runner().run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.ANY, NpcActionType.COST, "50"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "bought")),
                        false);

        assertThat(economy.withdrawals).containsExactly(new BigDecimal("50")); // charged exactly once (the failed try)
        assertThat(commandRunner.playerCommands).isEmpty();
        assertThat(messages.sentKeys).contains("npc.action.cost-denied");
    }

    @Test
    void costGateIsSkippedWhenNoEconomyProviderIsPresent() {
        runnerWith(Optional.empty())
                .run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.ANY, NpcActionType.COST, "50"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "free")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("free");
    }

    // --- GIVE -----------------------------------------------------------------------------------------------

    @Test
    void giveAddsTheItemToTheViewerInventory() {
        runner().run(player, List.of(new NpcAction(ClickTrigger.ANY, NpcActionType.GIVE, "DIAMOND:3")), false);

        assertThat(player.getInventory().contains(Material.DIAMOND, 3)).isTrue();
    }

    @Test
    void giveWithUnknownMaterialSkipsAndStillRunsTheRest() {
        runner().run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.ANY, NpcActionType.GIVE, "NOT_A_REAL_MATERIAL"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "next")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("next");
    }

    @Test
    void giveOverflowDropsAtTheViewerLocation() {
        // Fill every inventory slot so nothing fits, forcing an overflow drop.
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        }

        runner().run(player, List.of(new NpcAction(ClickTrigger.ANY, NpcActionType.GIVE, "DIAMOND:5")), false);

        long droppedDiamonds = player.getWorld().getEntities().stream()
                .filter(e -> e instanceof org.bukkit.entity.Item)
                .map(e -> ((org.bukkit.entity.Item) e).getItemStack())
                .filter(stack -> stack.getType() == Material.DIAMOND)
                .mapToInt(ItemStack::getAmount)
                .sum();
        assertThat(droppedDiamonds).isEqualTo(5);
    }

    // --- ordering -------------------------------------------------------------------------------------------

    @Test
    void aGateStopsTheActionsAfterItButNotThoseBefore() {
        runner().run(
                        player,
                        List.of(
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "first"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.CHANCE, "0"),
                                new NpcAction(ClickTrigger.ANY, NpcActionType.RUN_PLAYER, "second")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("first");
    }

    // --- SoundSpec (unchanged behaviour) --------------------------------------------------------------------

    @Test
    void keepsTheNamespaceOfANamespacedSoundKey() {
        BukkitNpcActionRunner.SoundSpec spec = BukkitNpcActionRunner.SoundSpec.parse("minecraft:entity.player.levelup");

        assertThat(spec.key()).isEqualTo("minecraft:entity.player.levelup");
        assertThat(spec.volume()).isEqualTo(1.0f);
        assertThat(spec.pitch()).isEqualTo(1.0f);
    }

    // --- fakes ----------------------------------------------------------------------------------------------

    private static final class RecordingRunner implements NpcCommandRunner {
        private final List<String> consoleCommands = new ArrayList<>();
        private final List<String> playerCommands = new ArrayList<>();

        @Override
        public void runAsConsole(String command) {
            consoleCommands.add(command);
        }

        @Override
        public void runAsPlayer(Player player, String command) {
            playerCommands.add(command);
        }
    }

    private static final class RecordingConnector implements NpcServerConnector {
        private final List<String> servers = new ArrayList<>();

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void connect(Player player, String server) {
            servers.add(server);
        }
    }

    /**
     * Captures the delayed continuations the DELAY action parks via {@link Scheduler#asyncAfter}; running them
     * resumes the chain synchronously in the test. An entity hop ({@code onEntity}) runs inline unless the player
     * has been disconnected, mirroring the Folia adapter's offline no-op.
     */
    private static final class CapturingScheduler implements Scheduler {
        private final Deque<Runnable> delayed = new ArrayDeque<>();
        private final Set<java.util.UUID> offline = new HashSet<>();

        int pendingDelays() {
            return delayed.size();
        }

        void disconnect(Player who) {
            offline.add(who.getUniqueId());
        }

        void runAllDelayed() {
            while (!delayed.isEmpty()) {
                delayed.poll().run();
            }
        }

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
            if (!offline.contains(player.uuid())) {
                task.run();
            }
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            delayed.add(task);
        }
    }

    private static final class FakePermissions implements Permissions {
        private final Set<String> granted = new HashSet<>();

        void grant(Player who, String node) {
            granted.add(who.getUniqueId() + ":" + node);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return granted.contains(who.uuid() + ":" + node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who,
                QuotaFamily family,
                com.uxplima.uxmessentials.shared.domain.@org.jspecify.annotations.Nullable WorldRef world,
                long def) {
            return QuotaResult.limited(def);
        }
    }

    private static final class RecordingEconomy implements NpcEconomy {
        private final List<BigDecimal> withdrawals = new ArrayList<>();
        private boolean affordable = true;

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
            withdrawals.add(amount);
            return affordable;
        }
    }

    private static final class RecordingMessages implements Messages {
        private final List<String> sentKeys = new ArrayList<>();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            sentKeys.add(key.key());
            return key.key();
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
}
