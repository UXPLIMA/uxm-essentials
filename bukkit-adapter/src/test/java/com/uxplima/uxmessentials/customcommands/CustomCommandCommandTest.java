package com.uxplima.uxmessentials.customcommands;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.customcommands.adapter.CustomCommandLoader;
import com.uxplima.uxmessentials.customcommands.adapter.inbound.command.CreateWizard;
import com.uxplima.uxmessentials.customcommands.adapter.inbound.command.CustomCommandCommand;
import com.uxplima.uxmessentials.customcommands.application.RunCustomCommand;
import com.uxplima.uxmessentials.customcommands.application.port.ActionRunner;
import com.uxplima.uxmessentials.customcommands.application.port.CommandFee;
import com.uxplima.uxmessentials.customcommands.domain.ActionChain;
import com.uxplima.uxmessentials.customcommands.domain.ChainDepth;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
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
 * Drives {@code /customcmd} over a real folder of definitions: what {@code list} and {@code info} report, that an
 * unknown id is refused rather than guessed at, that {@code reload} swaps the live catalog, that {@code run} for
 * somebody else needs its own node, and that {@code test} names the closed gate without stamping a cooldown,
 * charging anything or running a step.
 */
class CustomCommandCommandTest {

    private static final String ADMIN = "uxmessentials.customcommand.admin";
    private static final String RUN_OTHERS = "uxmessentials.customcommand.run.others";

    @TempDir
    Path folder;

    private ServerMock server;
    private PlayerMock operator;
    private AtomicReference<CustomCommandLoader.LoadResult> state;
    private CustomCommandLoader loader;
    private RecordingActions actions;
    private RecordingCooldowns cooldowns;
    private RecordingFee fee;
    private RunCustomCommand runner;
    private FakePermissions permissions;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        operator = server.addPlayer("Operator");
        operator.addAttachment(MockBukkit.createMockPlugin(), ADMIN, true);
        write("odul", """
                command {
                  name = "odul"
                  aliases = ["reward"]
                  permission = "uxmessentials.customcommand.odul"
                  cooldown = "30s"
                }
                arguments = [
                  { name = "target", type = online-player }
                  { name = "amount", type = int }
                ]
                actions = ["message:done", "broadcast:done"]
                """);
        write("gmc", """
                command { name = "gmc" }
                alias = "/gamemode creative"
                """);
        loader = new CustomCommandLoader(new SilentLogger());
        state = new AtomicReference<>(loaded());
        actions = new RecordingActions();
        cooldowns = new RecordingCooldowns();
        fee = new RecordingFee();
        permissions = new FakePermissions();
        runner = new RunCustomCommand(
                permissions,
                cooldowns,
                new UnusedWarmups(),
                actions,
                (who, requirements, arguments) -> true,
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
    void listNamesEveryLoadedCommandAndThenTheWarnings() throws Exception {
        state.set(new CustomCommandLoader.LoadResult(
                state.get().catalog(),
                state.get().argumentSpecs(),
                List.of(),
                List.of("dropping alias 'reward' of 'gmc': already taken")));

        run("customcmd list");

        assertThat(replies())
                .anySatisfy(line -> assertThat(line).contains("list.header", "count=2"))
                .anySatisfy(line -> assertThat(line).contains("list.entry", "id=odul"))
                .anySatisfy(line -> assertThat(line).contains("list.entry", "id=gmc"))
                .anySatisfy(line -> assertThat(line).contains("list.warning", "already taken"));
    }

    @Test
    void listSaysSoWhenNothingLoaded() throws Exception {
        state.set(CustomCommandLoader.LoadResult.empty());

        run("customcmd list");

        assertThat(replies()).singleElement().asString().contains("list.empty");
    }

    @Test
    void infoPrintsTheGatesArgumentsAndChainCounts() throws Exception {
        run("customcmd info odul");

        assertThat(replies())
                .anySatisfy(line -> assertThat(line).contains("info.header", "id=odul", "aliases=reward"))
                .anySatisfy(line -> assertThat(line)
                        .contains("info.gates", "permission=uxmessentials.customcommand.odul", "cooldown=30s"))
                .anySatisfy(line -> assertThat(line).contains("info.argument", "name=target"))
                .anySatisfy(line -> assertThat(line).contains("info.argument", "name=amount"))
                .anySatisfy(line -> assertThat(line).contains("info.chain", "actions=2"));
    }

    @Test
    void infoOnAnUnknownIdSaysNotFound() throws Exception {
        int result = run("customcmd info nope");

        assertThat(result).isZero();
        assertThat(replies()).singleElement().asString().contains("not-found", "id=nope");
    }

    @Test
    void reloadSwapsTheCatalogAndReportsTheCounts() throws Exception {
        write("hoscakal", """
                command { name = "hoscakal" }
                actions = ["message:bye"]
                """);

        run("customcmd reload");

        assertThat(replies()).singleElement().asString().contains("reloaded", "loaded=3");
        assertThat(state.get().catalog().ids()).contains("hoscakal");
    }

    @Test
    void runDispatchesForTheSenderWhenNoTargetIsGiven() throws Exception {
        run("customcmd run gmc");

        assertThat(actions.actors).containsExactly(operator.getUniqueId());
        assertThat(replies()).anySatisfy(line -> assertThat(line).contains("run.dispatched", "id=gmc"));
    }

    @Test
    void runForAnotherPlayerNeedsTheRunOthersNode() throws Exception {
        PlayerMock other = server.addPlayer("Other");

        int denied = run("customcmd run gmc Other");
        assertThat(denied).isZero();
        assertThat(actions.actors).isEmpty();
        assertThat(replies()).anySatisfy(line -> assertThat(line).contains("run.others-denied"));

        operator.addAttachment(MockBukkit.createMockPlugin(), RUN_OTHERS, true);
        run("customcmd run gmc Other");
        assertThat(actions.actors).containsExactly(other.getUniqueId());
    }

    @Test
    void testNamesTheFirstClosedGateAndChangesNothing() throws Exception {
        permissions.grant("uxmessentials.customcommand.odul");
        cooldowns.remaining = Duration.ofSeconds(12);

        run("customcmd test odul");

        assertThat(replies()).singleElement().asString().contains("test.blocked", "gate=cooldown");
        assertThat(cooldowns.stamped).isEmpty();
        assertThat(fee.charged).isZero();
        assertThat(actions.actors).isEmpty();
    }

    private CustomCommandLoader.LoadResult loaded() {
        return loader.loadFrom(folder, ActionChain.ChainLimits.defaults());
    }

    private void write(String id, String body) throws IOException {
        Files.writeString(folder.resolve(id + ".conf"), body);
    }

    /** Run {@code input} through a dispatcher carrying only {@code /customcmd}, as the operator. */
    private int run(String input) throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher
                .getRoot()
                .addChild(new CustomCommandCommand(
                                state,
                                this::loaded,
                                id -> loader.loadOne(folder.resolve(id + ".conf"), ActionChain.ChainLimits.defaults()),
                                runner,
                                new SyncScheduler(),
                                new TokenMessages(),
                                new CreateWizard(
                                        (player, viewer, request, onSubmit, onCancel) -> onCancel.run(),
                                        folder,
                                        () -> Set.copyOf(state.get().catalog().ids()),
                                        new CommandFeedback(new TokenMessages()),
                                        id -> {},
                                        new SilentLogger()),
                                id -> "")
                        .build());
        return dispatcher.execute(input, CommandSourceStackMock.from(operator));
    }

    /** Every line the operator has been sent since the last drain, as plain text. */
    private List<String> replies() {
        List<String> lines = new ArrayList<>();
        String next = operator.nextMessage();
        while (next != null) {
            lines.add(next);
            next = operator.nextMessage();
        }
        return lines;
    }

    /** Renders every lookup as {@code <key>|<name>=<value>,...}, so a reply's key and placeholders are assertable. */
    private static final class TokenMessages implements Messages {

        @Override
        public String resolve(PlayerRef viewer, MessageKey lookup, Map<String, String> placeholders) {
            StringBuilder rendered = new StringBuilder(lookup.key());
            new TreeMap<>(placeholders)
                    .forEach((name, value) ->
                            rendered.append('|').append(name).append('=').append(value));
            return rendered.toString();
        }
    }

    /** Records which actor each dispatched chain ran for; the chain itself is irrelevant here. */
    private static final class RecordingActions implements ActionRunner {

        private final List<java.util.UUID> actors = new ArrayList<>();

        @Override
        public void run(PlayerRef actor, ActionChain chain, Map<String, String> arguments) {
            actors.add(actor.uuid());
        }
    }

    private static final class RecordingCooldowns implements Cooldowns {

        private final List<String> stamped = new ArrayList<>();
        private @Nullable Duration remaining;

        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok(Unit.INSTANCE);
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            Duration left = remaining;
            return left == null ? Result.ok(Unit.INSTANCE) : Result.err(left);
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {
            stamped.add(label);
        }
    }

    private static final class RecordingFee implements CommandFee {

        private double charged;

        @Override
        public boolean canAfford(PlayerRef who, double amount) {
            return true;
        }

        @Override
        public boolean charge(PlayerRef who, double amount) {
            charged += amount;
            return true;
        }

        @Override
        public String format(double amount) {
            return String.valueOf(amount);
        }
    }

    private static final class FakePermissions implements Permissions {

        private final Set<String> granted = new HashSet<>();

        void grant(String node) {
            granted.add(node);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return granted.contains(node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class UnusedWarmups implements Warmups {

        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            throw new IllegalStateException("no definition in this test declares a warmup");
        }
    }

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
