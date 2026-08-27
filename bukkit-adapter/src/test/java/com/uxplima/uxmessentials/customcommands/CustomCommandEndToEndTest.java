package com.uxplima.uxmessentials.customcommands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerMoveEvent;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.customcommands.adapter.CustomCommandLoader;
import com.uxplima.uxmessentials.customcommands.adapter.inbound.command.CustomCommandRegistration;
import com.uxplima.uxmessentials.customcommands.adapter.inbound.listener.CommandWarmupTracker;
import com.uxplima.uxmessentials.customcommands.adapter.outbound.MenuActionRunner;
import com.uxplima.uxmessentials.customcommands.adapter.outbound.MenuActionRunner.PrivilegedActions;
import com.uxplima.uxmessentials.customcommands.adapter.outbound.TrackingCommandWarmups;
import com.uxplima.uxmessentials.customcommands.application.RunCustomCommand;
import com.uxplima.uxmessentials.customcommands.application.port.CommandFee;
import com.uxplima.uxmessentials.customcommands.domain.ActionChain;
import com.uxplima.uxmessentials.customcommands.domain.ChainDepth;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * One definition on disk, taken the whole way: read by the loader, registered as a real command, dispatched by a
 * player, and walked through every gate in order. What this proves that the unit tests cannot is the ordering, and
 * the two places where getting the order wrong costs somebody money: the cost is charged only after the warmup
 * completes, and a warmup a player walks out of charges nothing and runs nothing.
 */
class CustomCommandEndToEndTest {

    private static final String NODE = "uxmessentials.customcommand.odul";
    private static final ActionChain.ChainLimits LIMITS = ActionChain.ChainLimits.defaults();

    @TempDir
    Path folder;

    private ServerMock server;
    private World world;
    private PlayerMock player;
    private RecordingActions registry;
    private DeferringScheduler scheduler;
    private CommandWarmupTracker tracker;
    private ScriptedWarmups warmups;
    private RecordingFee fee;
    private RecordingCooldowns cooldowns;
    private FakePermissions permissions;
    private final AtomicReference<Boolean> requirementsPass = new AtomicReference<>(true);
    private RunCustomCommand runner;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Runner");
        player.addAttachment(MockBukkit.createMockPlugin(), NODE, true);
        player.teleport(new Location(world, 10.5, 64, 10.5));
        write("""
                command {
                  name = "odul"
                  permission = "uxmessentials.customcommand.odul"
                  cooldown = "30s"
                  warmup = "3s"
                  cost = 100
                }
                arguments = [
                  { name = "amount", type = int }
                ]
                requirements = ["has-money:100"]
                requirement-deny = ["message:you cannot afford this"]
                actions = ["message:paid %arg_amount%", "delay:2s", "message:and again"]
                """);
        registry = new RecordingActions();
        scheduler = new DeferringScheduler();
        tracker = new CommandWarmupTracker();
        warmups = new ScriptedWarmups();
        fee = new RecordingFee();
        cooldowns = new RecordingCooldowns();
        permissions = new FakePermissions();
        permissions.grant(NODE);
        GuiText guiText = new GuiText(new KeyMessages());
        MenuRenderer renderer =
                new MenuRenderer(new ItemRenderer(guiText, new PlaceholderRegistry()), new ConditionRegistry());
        Menus menus = new Menus(
                renderer, scheduler, new ListSourceRegistry(), null, registry.registry(), new ConditionRegistry());
        runner = new RunCustomCommand(
                permissions,
                cooldowns,
                new TrackingCommandWarmups(warmups, tracker),
                new MenuActionRunner(menus, scheduler, new SilentLogger(), PrivilegedActions::defaults),
                (target, requirements, arguments) -> requirementsPass.get(),
                fee,
                (actor, command, outcome) -> {},
                new ChainDepth(5),
                new SilentLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aPlayerWithoutTheNodeIsRefusedAndNothingRuns() {
        permissions.revoke(NODE);
        PlayerMock stranger = server.addPlayer("Stranger");

        // The node gates the Brigadier requirement too, so the command is not merely refused: it is invisible.
        assertThatThrownBy(() -> dispatchAs(stranger, "odul 1")).isInstanceOf(CommandSyntaxException.class);
        assertThat(registry.dispatched()).isEmpty();
        assertThat(fee.charged).isZero();
    }

    @Test
    void aFailedRequirementRunsTheDenyChainOnly() throws Exception {
        requirementsPass.set(false);

        int result = dispatch("odul 1");

        assertThat(result).isZero();
        assertThat(registry.dispatched()).containsExactly("message:you cannot afford this");
        assertThat(warmups.started).isZero();
        assertThat(fee.charged).isZero();
    }

    @Test
    void theWarmupStartsAndTheCostIsNotChargedYet() throws Exception {
        dispatch("odul 1");

        assertThat(warmups.started).isEqualTo(1);
        assertThat(tracker.tracked()).isEqualTo(1);
        assertThat(fee.charged).isZero();
        assertThat(registry.dispatched()).isEmpty();
        assertThat(cooldowns.stamped).isEmpty();
    }

    @Test
    void movingDuringTheWarmupCancelsItAndChargesNothing() throws Exception {
        dispatch("odul 1");

        tracker.onMove(new PlayerMoveEvent(player, player.getLocation(), new Location(world, 12.5, 64, 12.5)));

        assertThat(warmups.cancelled).isTrue();
        assertThat(tracker.tracked()).isZero();
        assertThat(fee.charged).isZero();
        assertThat(registry.dispatched()).isEmpty();
        assertThat(cooldowns.stamped).isEmpty();
    }

    @Test
    void standingStillCompletesTheWarmupThenChargesThenRunsTheChain() throws Exception {
        dispatch("odul 4");

        warmups.complete();

        assertThat(fee.charged).isEqualTo(100);
        assertThat(cooldowns.stamped).containsExactly("customcommand.odul");
        assertThat(registry.dispatched()).containsExactly("message:paid 4");
        assertThat(tracker.tracked()).isZero();
    }

    @Test
    void theSecondRunInsideTheCooldownWindowIsRefused() throws Exception {
        dispatch("odul 1");
        warmups.complete();
        cooldowns.remaining = Duration.ofSeconds(25);

        int second = dispatch("odul 1");

        assertThat(second).isZero();
        assertThat(warmups.started).isEqualTo(1);
        assertThat(fee.charged).isEqualTo(100);
    }

    @Test
    void aDelayedStepRunsOnlyAfterTheSchedulerAdvances() throws Exception {
        dispatch("odul 2");
        warmups.complete();

        assertThat(registry.dispatched()).containsExactly("message:paid 2");

        scheduler.runPending();

        assertThat(registry.dispatched()).containsExactly("message:paid 2", "message:and again");
    }

    /** Load the folder, register the one definition it holds, and run {@code input} through it as the player. */
    private int dispatch(String input) throws CommandSyntaxException {
        return dispatchAs(player, input);
    }

    private int dispatchAs(PlayerMock sender, String input) throws CommandSyntaxException {
        CustomCommandLoader.LoadResult loaded = new CustomCommandLoader(new SilentLogger()).loadFrom(folder, LIMITS);
        CustomCommand command = loaded.catalog().byId("odul").orElseThrow();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher
                .getRoot()
                .addChild(new CustomCommandRegistration(
                                command, loaded.argumentSpecs().getOrDefault("odul", List.of()), runner)
                        .build());
        return dispatcher.execute(input, CommandSourceStackMock.from(sender));
    }

    private void write(String body) throws IOException {
        Files.writeString(folder.resolve("odul.conf"), body);
    }

    /** A warmup port the test drives by hand, so the countdown is a method call rather than a wall clock. */
    private final class ScriptedWarmups implements Warmups {

        private int started;
        private boolean cancelled;
        private @Nullable Runnable onComplete;
        private @Nullable ScriptedHandle handle;

        @Override
        public WarmupHandle begin(PlayerRef target, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            started++;
            this.onComplete = onComplete;
            ScriptedHandle live = new ScriptedHandle(onCancel);
            this.handle = live;
            return live;
        }

        /** Let the countdown elapse, which is what a player standing still for the whole warmup produces. */
        void complete() {
            ScriptedHandle live = handle;
            Runnable finish = onComplete;
            if (live == null || finish == null || live.cancelled) {
                return;
            }
            live.complete = true;
            finish.run();
        }

        private final class ScriptedHandle implements WarmupHandle {

            private final Runnable onCancel;
            private boolean cancelled;
            private boolean complete;

            ScriptedHandle(Runnable onCancel) {
                this.onCancel = onCancel;
            }

            @Override
            public void cancel() {
                if (cancelled || complete) {
                    return;
                }
                cancelled = true;
                ScriptedWarmups.this.cancelled = true;
                onCancel.run();
            }

            @Override
            public boolean isComplete() {
                return complete;
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        }
    }

    /** An action registry whose handlers record the token they were dispatched with, head and value alike. */
    private static final class RecordingActions {

        private final ActionRegistry registry = new ActionRegistry();
        private final List<String> dispatched = new ArrayList<>();

        RecordingActions() {
            for (String id : List.of("console", "command", "message", "broadcast")) {
                registry.register(
                        id, ctx -> dispatched.add(id + ":" + ctx.args().getOrDefault("value", "")));
            }
        }

        ActionRegistry registry() {
            return registry;
        }

        List<String> dispatched() {
            return dispatched;
        }
    }

    /** A scheduler that runs immediate work inline and holds delayed work until a test releases it. */
    private static final class DeferringScheduler implements Scheduler {

        private final List<Runnable> pending = new ArrayList<>();

        void runPending() {
            List<Runnable> due = List.copyOf(pending);
            pending.clear();
            due.forEach(Runnable::run);
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
        public void onEntity(PlayerRef target, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            pending.add(task);
        }
    }

    private static final class RecordingFee implements CommandFee {

        private double charged;

        @Override
        public boolean canAfford(PlayerRef target, double amount) {
            return true;
        }

        @Override
        public boolean charge(PlayerRef target, double amount) {
            charged += amount;
            return true;
        }

        @Override
        public String format(double amount) {
            return String.valueOf(amount);
        }
    }

    private static final class RecordingCooldowns implements Cooldowns {

        private final List<String> stamped = new ArrayList<>();
        private @Nullable Duration remaining;

        @Override
        public Result<Unit, Duration> check(PlayerRef target, CooldownKind kind) {
            return Result.ok(Unit.INSTANCE);
        }

        @Override
        public void stamp(PlayerRef target, CooldownKind kind) {}

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef target, String label) {
            Duration left = remaining;
            return left == null ? Result.ok(Unit.INSTANCE) : Result.err(left);
        }

        @Override
        public void stampLabel(PlayerRef target, String label) {
            stamped.add(label);
        }
    }

    private static final class FakePermissions implements Permissions {

        private final Set<String> granted = new HashSet<>();

        void grant(String node) {
            granted.add(node);
        }

        void revoke(String node) {
            granted.remove(node);
        }

        @Override
        public boolean has(PlayerRef target, String node) {
            return granted.contains(node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef target, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class KeyMessages implements Messages {

        @Override
        public String resolve(PlayerRef viewer, MessageKey lookup, java.util.Map<String, String> placeholders) {
            return lookup.key();
        }
    }

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
