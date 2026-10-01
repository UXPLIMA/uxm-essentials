package com.uxplima.uxmessentials.communication.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;

/**
 * Whoever clears the chat is told it is done, the console included.
 *
 * <p>The console ran {@code /clearchat} and read nothing back, so an operator could not tell the command had run.
 */
class ClearChatCommandTest {

    private ServerMock server;
    private final List<String> delivered = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the console that clears the chat reads the confirmation")
    void theConsoleIsTold() throws Exception {
        Messages messages = (viewer, key, values) -> key.key();
        MessageSink sink = (viewer, text) -> {
            if (viewer.isSystem()) {
                delivered.add(text);
            }
        };
        ClearChatCommand command = new ClearChatCommand(messages, new Notifier(messages, sink), sink, new Inline());
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());

        dispatcher.execute("clearchat", CommandSourceStackMock.from(server.getConsoleSender()));

        assertThat(delivered).containsExactly("communication.clearchat.by");
    }

    private static final class Inline implements Scheduler {
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
