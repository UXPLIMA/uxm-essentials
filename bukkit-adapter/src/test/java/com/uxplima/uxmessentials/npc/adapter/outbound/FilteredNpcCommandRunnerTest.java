package com.uxplima.uxmessentials.npc.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.npc.adapter.inbound.listener.NpcCommandRunner;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link FilteredNpcCommandRunner}: a blocked command is dropped before the delegate on every dispatch path
 * (console / player / op), while an allowed command passes straight through. The delegate is a recording fake, so
 * no Bukkit dispatch happens.
 */
class FilteredNpcCommandRunnerTest {

    @Test
    void dropsABlockedConsoleCommand() {
        RecordingRunner delegate = new RecordingRunner();
        FilteredNpcCommandRunner runner =
                new FilteredNpcCommandRunner(delegate, BlockedCommands.of(List.of("stop")), new NoOpLogger());

        runner.runAsConsole("stop");

        assertThat(delegate.consoleCommands).isEmpty();
    }

    @Test
    void passesAnAllowedConsoleCommandToTheDelegate() {
        RecordingRunner delegate = new RecordingRunner();
        FilteredNpcCommandRunner runner =
                new FilteredNpcCommandRunner(delegate, BlockedCommands.of(List.of("stop")), new NoOpLogger());

        runner.runAsConsole("say hello");

        assertThat(delegate.consoleCommands).containsExactly("say hello");
    }

    @Test
    void filtersThePlayerAndOpDispatchesToo() {
        RecordingRunner delegate = new RecordingRunner();
        FilteredNpcCommandRunner runner =
                new FilteredNpcCommandRunner(delegate, BlockedCommands.of(List.of("op")), new NoOpLogger());
        Player player = mock(Player.class);

        runner.runAsPlayer(player, "op Notch");
        runner.runAsPlayerOp(player, "give @s diamond");

        assertThat(delegate.playerCommands).isEmpty();
        assertThat(delegate.opCommands).containsExactly("give @s diamond");
    }

    private static final class RecordingRunner implements NpcCommandRunner {
        private final List<String> consoleCommands = new ArrayList<>();
        private final List<String> playerCommands = new ArrayList<>();
        private final List<String> opCommands = new ArrayList<>();

        @Override
        public void runAsConsole(String command) {
            consoleCommands.add(command);
        }

        @Override
        public void runAsPlayer(Player player, String command) {
            playerCommands.add(command);
        }

        @Override
        public void runAsPlayerOp(Player player, String command) {
            opCommands.add(command);
        }
    }

    private static final class NoOpLogger implements Logger {
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
