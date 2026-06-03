package com.uxplima.uxmessentials.moderation.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.moderation.adapter.inbound.command.SudoCommand;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@code /sudo <player> <command>}: dispatch a command as another player on the target's
 * own region thread. The {@link RunImmediately} scheduler runs the queued task inline so the assertion sees the
 * effect synchronously. A throwaway {@code echo} command echoes its args back to whoever ran it, so the target's
 * message queue proves the dispatch ran under the target's identity. A matched target runs the stripped command
 * and the actor gets the confirmation; an unknown target yields the not-found reply and no dispatch.
 */
class SudoCommandTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String PERMISSION = "uxmessentials.moderation.sudo";

    private ServerMock server;
    private RunImmediately scheduler;
    private RecordingSink sink;
    private SudoCommand command;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        server.getCommandMap().register("test", new EchoCommand());
        scheduler = new RunImmediately();
        sink = new RecordingSink();
        command = new SudoCommand(scheduler, new EchoMessages(), sink);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theLiteralIsSudo() {
        assertThat(command.build().getLiteral()).isEqualTo("sudo");
    }

    @Test
    void runsTheCommandAsTheTarget() {
        PlayerMock target = server.addPlayer("Subject");
        PlayerMock actor = server.addPlayer("Staff");
        actor.addAttachment(MockBukkit.createMockPlugin(), PERMISSION, true);

        execute(CommandSourceStackMock.from(actor), "sudo Subject echo hi there");

        assertThat(target.nextMessage()).isEqualTo("echoed: hi there");
        assertThat(scheduler.entityTasks).isOne();
    }

    @Test
    void stripsASingleLeadingSlash() {
        PlayerMock target = server.addPlayer("Subject");
        PlayerMock actor = server.addPlayer("Staff");
        actor.addAttachment(MockBukkit.createMockPlugin(), PERMISSION, true);

        execute(CommandSourceStackMock.from(actor), "sudo Subject /echo slashed");

        assertThat(target.nextMessage()).isEqualTo("echoed: slashed");
    }

    @Test
    void confirmsToTheActor() {
        server.addPlayer("Subject");
        PlayerMock actor = server.addPlayer("Staff");
        actor.addAttachment(MockBukkit.createMockPlugin(), PERMISSION, true);

        execute(CommandSourceStackMock.from(actor), "sudo Subject echo hi");

        String line = PLAIN.serialize(sink.last());
        assertThat(line).contains("done").contains("player=Subject").contains("command=echo hi");
    }

    @Test
    void anUnknownTargetYieldsNotFoundAndNoDispatch() {
        PlayerMock actor = server.addPlayer("Staff");
        actor.addAttachment(MockBukkit.createMockPlugin(), PERMISSION, true);

        execute(CommandSourceStackMock.from(actor), "sudo Nobody echo hi");

        assertThat(scheduler.entityTasks).isZero();
        String line = PLAIN.serialize(sink.last());
        assertThat(line).contains("unknown").contains("player=Nobody");
    }

    private void execute(CommandSourceStack source, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());
        try {
            dispatcher.execute(input, source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    /** A throwaway command that echoes its joined args back to whoever ran it, proving the run identity. */
    private static final class EchoCommand extends Command {
        EchoCommand() {
            super("echo");
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            sender.sendMessage("echoed: " + String.join(" ", args));
            return true;
        }
    }

    /** Runs every queued task inline so the dispatch effect is observable in the same call. */
    private static final class RunImmediately implements Scheduler {
        private int entityTasks;

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
            entityTasks++;
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

    /** Captures the rendered confirmation lines so the actor-facing reply is assertable. */
    private static final class RecordingSink implements MessageSink {
        private final List<net.kyori.adventure.text.Component> lines = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            lines.add(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize(renderedText));
        }

        net.kyori.adventure.text.Component last() {
            return lines.get(lines.size() - 1);
        }
    }

    /** Echoes the resolved key's suffix and placeholders so the rendered reply is assertable. */
    private static final class EchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            String suffix = key == ModerationMessageKey.SUDO_DONE ? "done" : "unknown";
            StringBuilder out = new StringBuilder(suffix);
            placeholders.forEach(
                    (name, value) -> out.append(' ').append(name).append('=').append(value));
            return out.toString();
        }
    }
}
