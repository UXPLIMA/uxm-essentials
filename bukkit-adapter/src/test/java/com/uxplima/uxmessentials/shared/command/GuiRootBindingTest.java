package com.uxplima.uxmessentials.shared.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.GuiRootBinding;
import com.uxplima.uxmessentials.shared.application.command.CommandId;
import com.uxplima.uxmessentials.shared.application.command.EffectiveCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;

/**
 * The GUI binding turns a command's catalog {@code gui} flag into the bare-input behaviour: when on, the
 * command's {@link CommandRegistration#guiRoot()} opener is installed as the root executor (replacing any
 * executor the command shipped with), and the children carry across verbatim; when off, the node is left
 * untouched so the later usage binding can give its usage-text fallback on an arg-only root. A command that
 * exposes no opener is never reshaped regardless of the flag. MockBukkit boots Paper's Brigadier so the
 * {@link Commands#literal} rebuild is wired before the nodes are built.
 */
class GuiRootBindingTest {

    private static final Command<CommandSourceStack> OPENER = c -> 7;

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void openerInstalledAsRootWhenGuiOn() {
        GuiRootBinding binding = guiBinding("kit", true);

        LiteralCommandNode<CommandSourceStack> built =
                binding.wrap(new OpenerStub("kit")).build();

        assertThat(built.getLiteral()).isEqualTo("kit");
        assertThat(run(built, "kit", server.addPlayer("Alice"))).isEqualTo(7);
        assertThat(built.getChild("list")).isNotNull();
    }

    @Test
    void rootLeftWithoutExecutorWhenGuiOff() {
        GuiRootBinding binding = guiBinding("kit", false);

        LiteralCommandNode<CommandSourceStack> built =
                binding.wrap(new OpenerStub("kit")).build();

        // gui off leaves the arg-only root bare, so the usage binding can later inject its usage executor
        assertThat(built.getCommand()).isNull();
        assertThat(built.getChild("list")).isNotNull();
    }

    @Test
    void openerReplacesExistingRootCommandWhenGuiOn() {
        GuiRootBinding binding = guiBinding("uxmess", true);

        LiteralCommandNode<CommandSourceStack> built =
                binding.wrap(new HelpRootStub("uxmess")).build();

        // the shipped help root executor is swapped for the GUI opener
        assertThat(run(built, "uxmess", server.addPlayer("Alice"))).isEqualTo(7);
    }

    /**
     * The console cannot see a screen. {@code /banlist}, {@code /eco} and {@code /uxmess} opened nothing for it and
     * said nothing, or refused it as player-only, so an operator at the console got no answer at all. The command's
     * own root runs instead, which for {@code /uxmess} is the help and for {@code /banlist} is the list in text.
     */
    @Test
    void theConsoleRunsTheRootTheCommandShippedWith() {
        GuiRootBinding binding = guiBinding("uxmess", true);

        LiteralCommandNode<CommandSourceStack> built =
                binding.wrap(new HelpRootStub("uxmess")).build();

        assertThat(run(built, "uxmess", server.getConsoleSender())).isEqualTo(99);
    }

    @Test
    void commandWithNoOpenerIsUnchangedEvenWhenGuiOn() {
        GuiRootBinding binding = guiBinding("home", true);
        CommandRegistration delegate = new NoOpenerStub("home");

        CommandRegistration wrapped = binding.wrap(delegate);

        // no guiRoot means nothing to install, so the original node is returned untouched
        assertThat(wrapped.build().getCommand()).isNull();
    }

    @Test
    void unknownCommandIdDefaultsToGuiOn() {
        GuiRootBinding binding = new GuiRootBinding(Map.of());

        LiteralCommandNode<CommandSourceStack> built =
                binding.wrap(new OpenerStub("kit")).build();

        assertThat(run(built, "kit", server.addPlayer("Alice"))).isEqualTo(7);
    }

    private static int run(LiteralCommandNode<CommandSourceStack> node, String input, CommandSender sender) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
        try {
            return dispatcher.execute(input, CommandSourceStackMock.from(sender));
        } catch (CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private static GuiRootBinding guiBinding(String id, boolean gui) {
        return new GuiRootBinding(Map.of(id, new EffectiveCommand(new CommandId(id), id, List.of(), true, gui)));
    }

    /** An arg-only root with no root executor that opens a GUI on bare input (the {@code /kit} shape). */
    private record OpenerStub(String id) implements CommandRegistration {
        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            return Commands.literal(id)
                    .then(Commands.literal("list").executes(c -> 1))
                    .build();
        }

        @Override
        public String description() {
            return "x";
        }

        @Override
        public String commandId() {
            return id;
        }

        @Override
        public Optional<Command<CommandSourceStack>> guiRoot() {
            return Optional.of(OPENER);
        }
    }

    /** A root that ships with its own executor and also exposes a GUI opener (the {@code /uxmess} shape). */
    private record HelpRootStub(String id) implements CommandRegistration {
        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            return Commands.literal(id)
                    .executes(c -> 99)
                    .then(Commands.literal("status").executes(c -> 1))
                    .build();
        }

        @Override
        public String description() {
            return "x";
        }

        @Override
        public String commandId() {
            return id;
        }

        @Override
        public Optional<Command<CommandSourceStack>> guiRoot() {
            return Optional.of(OPENER);
        }
    }

    /** A command that does not open a GUI on bare input: must never be reshaped. */
    private record NoOpenerStub(String id) implements CommandRegistration {
        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            return Commands.literal(id)
                    .then(Commands.literal("set").executes(c -> 1))
                    .build();
        }

        @Override
        public String description() {
            return "x";
        }

        @Override
        public String commandId() {
            return id;
        }
    }
}
