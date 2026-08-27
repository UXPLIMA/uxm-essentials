package com.uxplima.uxmessentials.shared.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentNodes;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentSpec.ArgType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Real Brigadier dispatch over the shared argument builder: a trailing optional argument may be omitted (and reads
 * back as the empty string), a greedy last string swallows the rest of the line, numeric bounds are enforced by the
 * node itself, and an argument that sits before an optional one stays required.
 */
class ArgumentNodesTest {

    private ServerMock server;
    private PlayerMock runner;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        runner = server.addPlayer("Runner");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aTrailingOptionalArgumentMakesTheShorterFormExecutable() throws Exception {
        List<ArgumentSpec> args = List.of(
                new ArgumentSpec("target", ArgType.STRING),
                new ArgumentSpec("reason", ArgType.STRING, false, true, Optional.empty(), Optional.empty()));

        assertThat(dispatch(args, "test Steve griefing"))
                .containsEntry("target", "Steve")
                .containsEntry("reason", "griefing");
        assertThat(dispatch(args, "test Steve"))
                .containsEntry("target", "Steve")
                .containsEntry("reason", "");
    }

    @Test
    void aGreedyLastStringCapturesTheRestOfTheLine() throws Exception {
        List<ArgumentSpec> args = List.of(new ArgumentSpec("message", ArgType.STRING, true));

        assertThat(dispatch(args, "test hello there world")).containsEntry("message", "hello there world");
    }

    @Test
    void anIntArgumentWithBoundsRejectsAnOutOfRangeValue() throws Exception {
        List<ArgumentSpec> args =
                List.of(new ArgumentSpec("amount", ArgType.INT, false, false, Optional.of(1.0), Optional.of(64.0)));

        assertThat(dispatch(args, "test 5")).containsEntry("amount", "5");
        assertThatThrownBy(() -> dispatch(args, "test 99")).isInstanceOf(CommandSyntaxException.class);
    }

    @Test
    void everyArgumentBeforeAnOptionalOneStaysRequired() {
        List<ArgumentSpec> args = List.of(
                new ArgumentSpec("target", ArgType.STRING),
                new ArgumentSpec("reason", ArgType.STRING, false, true, Optional.empty(), Optional.empty()));

        assertThatThrownBy(() -> dispatch(args, "test")).isInstanceOf(CommandSyntaxException.class);
    }

    /** Register a throwaway {@code /test} built from {@code args} and return the values the executor read back. */
    private Map<String, String> dispatch(List<ArgumentSpec> args, String input) throws CommandSyntaxException {
        AtomicReference<Map<String, String>> captured = new AtomicReference<>(Map.of());
        Command<CommandSourceStack> executor = ctx -> {
            captured.set(ArgumentNodes.read(ctx, args));
            return Command.SINGLE_SUCCESS;
        };
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher
                .getRoot()
                .addChild(Commands.literal("test")
                        .then(ArgumentNodes.chain(args, executor))
                        .build());
        dispatcher.execute(input, CommandSourceStackMock.from(runner));
        return captured.get();
    }
}
