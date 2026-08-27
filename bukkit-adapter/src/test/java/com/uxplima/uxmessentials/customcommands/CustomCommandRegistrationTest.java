package com.uxplima.uxmessentials.customcommands;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.customcommands.adapter.CustomCommandLoader;
import com.uxplima.uxmessentials.customcommands.adapter.inbound.command.CustomCommandRegistration;
import com.uxplima.uxmessentials.customcommands.application.RunCustomCommand;
import com.uxplima.uxmessentials.customcommands.application.RunOutcome;
import com.uxplima.uxmessentials.customcommands.application.port.ActionRunner;
import com.uxplima.uxmessentials.customcommands.application.port.CommandFee;
import com.uxplima.uxmessentials.customcommands.application.port.RunFeedback;
import com.uxplima.uxmessentials.customcommands.domain.ActionChain;
import com.uxplima.uxmessentials.customcommands.domain.ArgumentKind;
import com.uxplima.uxmessentials.customcommands.domain.ChainDepth;
import com.uxplima.uxmessentials.customcommands.domain.CommandArgument;
import com.uxplima.uxmessentials.customcommands.domain.CommandLiteral;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommandId;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Real Brigadier dispatch over one operator-defined command: the declared literal is what registers, the declared
 * permission gates whether a sender even sees the node, the parsed arguments (plus the raw remainder) reach the use
 * case, an omitted trailing optional argument arrives as the empty string, a console sender is marked as one, and a
 * closed gate is reported to the server as a failed dispatch rather than a silent success.
 */
class CustomCommandRegistrationTest {

    private static final String NODE = "uxmessentials.customcommand.odul";

    private ServerMock server;
    private PlayerMock runner;
    private RecordingActions actions;
    private FakePermissions permissions;
    private RunCustomCommand useCase;
    private boolean requirementsPass = true;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        runner = server.addPlayer("Runner");
        actions = new RecordingActions();
        permissions = new FakePermissions();
        useCase = new RunCustomCommand(
                permissions,
                new FreeCooldowns(),
                new UnusedWarmups(),
                actions,
                (who, requirements, arguments) -> requirementsPass,
                new FreeFee(),
                new SilentFeedback(),
                new ChainDepth(5),
                new SilentLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void registersTheDeclaredLiteralWithItsAliases() {
        CustomCommandRegistration registration = registrationFor(definition(Optional.empty()));

        assertThat(registration.build().getLiteral()).isEqualTo("odul");
        assertThat(registration.aliases()).containsExactly("reward");
        assertThat(registration.commandId()).isEqualTo("custom:odul");
        assertThat(registration.defaultName()).isEqualTo("odul");
        assertThat(registration.defaultAliases()).containsExactly("reward");
    }

    @Test
    void aPermissionedCommandIsHiddenFromASenderWhoLacksTheNode() {
        CustomCommandRegistration registration = registrationFor(definition(Optional.of(NODE)));
        var node = registration.build();

        assertThat(node.getRequirement().test(CommandSourceStackMock.from(runner)))
                .isFalse();
        runner.addAttachment(MockBukkit.createMockPlugin(), NODE, true);
        assertThat(node.getRequirement().test(CommandSourceStackMock.from(runner)))
                .isTrue();
    }

    @Test
    void runningTheCommandHandsTheParsedArgumentsToTheUseCase() throws Exception {
        dispatch(definition(Optional.empty()), "odul Alex 3 for helping", CommandSourceStackMock.from(runner));

        assertThat(actions.arguments)
                .containsEntry("target", "Alex")
                .containsEntry("amount", "3")
                .containsEntry("reason", "for helping")
                .containsEntry("args", "Alex 3 for helping");
    }

    @Test
    void anOmittedTrailingOptionalArgumentReachesTheUseCaseAsAnEmptyString() throws Exception {
        dispatch(definition(Optional.empty()), "odul Alex 3", CommandSourceStackMock.from(runner));

        assertThat(actions.arguments).containsEntry("reason", "").containsEntry("args", "Alex 3");
    }

    @Test
    void aConsoleSenderReachesTheUseCaseMarkedAsConsole() throws Exception {
        dispatch(definition(Optional.empty()), "odul Alex 3", CommandSourceStackMock.from(server.getConsoleSender()));

        assertThat(actions.actor).isNotNull();
        assertThat(actions.actor.isSystem()).isTrue();
    }

    @Test
    void aFailedGateIsReportedAsAFailedDispatch() throws Exception {
        int permitted = dispatch(definition(Optional.empty()), "odul Alex 3", CommandSourceStackMock.from(runner));
        requirementsPass = false;
        int refused = dispatch(definition(Optional.empty()), "odul Alex 3", CommandSourceStackMock.from(runner));

        assertThat(permitted).isEqualTo(Command.SINGLE_SUCCESS);
        assertThat(refused).isZero();
    }

    /** Register {@code command} on a throwaway dispatcher and run {@code input} through it as {@code source}. */
    private int dispatch(CustomCommand command, String input, CommandSourceStack source) throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(registrationFor(command).build());
        return dispatcher.execute(input, source);
    }

    private CustomCommandRegistration registrationFor(CustomCommand command) {
        return new CustomCommandRegistration(command, CustomCommandLoader.specsFor(command), useCase);
    }

    /** {@code /odul <target> <amount> [reason]}, optionally gated by the declared node. */
    private static CustomCommand definition(Optional<String> permission) {
        return new CustomCommand(
                CustomCommandId.of("odul"),
                new CommandLiteral("odul", List.of("reward"), Map.of()),
                permission,
                Optional.empty(),
                true,
                "Reward a player",
                Optional.empty(),
                Duration.ZERO,
                Duration.ZERO,
                0,
                List.of(
                        CommandArgument.of("target", ArgumentKind.STRING),
                        CommandArgument.of("amount", ArgumentKind.INT),
                        new CommandArgument(
                                "reason", ArgumentKind.STRING, true, true, Optional.empty(), Optional.empty())),
                List.of(),
                ActionChain.empty(),
                ActionChain.of(List.of("message:done"), ActionChain.ChainLimits.defaults()));
    }

    /** Captures what the use case handed the action chain, which is what the registration parsed. */
    private static final class RecordingActions implements ActionRunner {

        private @Nullable PlayerRef actor;
        private Map<String, String> arguments = Map.of();

        @Override
        public void run(PlayerRef actor, ActionChain chain, Map<String, String> arguments) {
            this.actor = actor;
            this.arguments = Map.copyOf(arguments);
        }
    }

    private static final class FakePermissions implements Permissions {

        private final Set<String> granted = new HashSet<>();

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

    private static final class FreeCooldowns implements Cooldowns {

        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok(Unit.INSTANCE);
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok(Unit.INSTANCE);
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class UnusedWarmups implements Warmups {

        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            throw new IllegalStateException("no definition in this test declares a warmup");
        }
    }

    private static final class FreeFee implements CommandFee {

        @Override
        public boolean canAfford(PlayerRef who, double amount) {
            return true;
        }

        @Override
        public boolean charge(PlayerRef who, double amount) {
            return true;
        }

        @Override
        public String format(double amount) {
            return String.valueOf(amount);
        }
    }

    private static final class SilentFeedback implements RunFeedback {

        @Override
        public void report(PlayerRef actor, CustomCommand command, RunOutcome outcome) {}
    }

    private static final class SilentLogger implements Logger {

        private final List<String> lines = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            lines.add(message);
        }

        @Override
        public void warn(String message, Object... args) {
            lines.add(message);
        }

        @Override
        public void error(String message, Throwable cause) {
            lines.add(message);
        }

        @Override
        public void debug(String message, Object... args) {
            lines.add(message);
        }
    }
}
