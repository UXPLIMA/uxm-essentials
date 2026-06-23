package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.plugin.Plugin;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Drives the seam end to end through its real chat backend (the anvil backend cannot open a live anvil under
 * MockBukkit, so the dispatch test asserts which backend a key selects, and the submit/cancel routing is exercised
 * through chat where the {@code AsyncChatEvent} round-trips): a configured chat key sends the prompt as a chat message
 * and the player's next line routes to {@code onSubmit}; a cancel keyword in either backend resolves to {@code onCancel}
 * with the cancel acknowledgement; an anvil key sends no chat message. This is the per-input anvil/chat choice plus the
 * shared cancel keyword the operator controls.
 */
class TextInputTest {

    private static final MessageKey LABEL = GuiMessageKey.INPUT_CANCELLED;

    @TempDir
    Path dir;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private TextInput textInput;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        Files.writeString(dir.resolve("config.conf"), """
                default-mode = anvil
                modes {
                  "the.chat-key" = chat
                  "the.anvil-key" = anvil
                }
                cancel-keywords = ["cancel", "iptal"]
                """);
        GuiText guiText = new GuiText(new KeyMessages());
        // install() registers the chat backend so the fired AsyncChatEvent routes through it; MockBukkit unmock drops
        // it.
        textInput = TextInputTestKit.install(plugin, guiText, new SyncScheduler(), dir, new SilentLogger())
                .textInput();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aChatKeySendsThePromptAndRoutesTheNextLineToSubmit() {
        AtomicReference<String> submitted = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        textInput.prompt(
                player, viewer, InputRequest.of("the.chat-key", LABEL), submitted::set, () -> cancelled.set(true));

        // chat mode opens by sending the prompt as a message
        assertThat(player.nextMessage()).isNotNull();
        fireChat("HomeBase");

        assertThat(submitted.get()).isEqualTo("HomeBase");
        assertThat(cancelled.get()).isFalse();
    }

    @Test
    void aCancelKeywordRoutesToCancelAndSendsTheAcknowledgement() {
        AtomicReference<String> submitted = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        textInput.prompt(
                player, viewer, InputRequest.of("the.chat-key", LABEL), submitted::set, () -> cancelled.set(true));
        player.nextMessage(); // drop the prompt line
        fireChat("iptal");

        assertThat(submitted.get()).isNull();
        assertThat(cancelled.get()).isTrue();
        // the centralised cancel acknowledgement (gui.input.cancelled) is sent by the seam
        assertThat(player.nextMessage()).contains(GuiMessageKey.INPUT_CANCELLED.key());
    }

    @Test
    void anAnvilKeyDoesNotSendAChatPrompt() {
        // The anvil backend tries to open an anvil (a no-op result under MockBukkit), but it never sends a chat line;
        // that is the observable difference from a chat-mode key with the same call.
        AtomicReference<String> submitted = new AtomicReference<>();
        try {
            textInput.prompt(player, viewer, InputRequest.of("the.anvil-key", LABEL), submitted::set, () -> {});
        } catch (RuntimeException anvilUnsupportedUnderMock) {
            // MockBukkit's openAnvil is unimplemented; the dispatch (no chat message) is what this test pins.
        }
        assertThat(player.nextMessage()).isNull();
    }

    private void fireChat(String line) {
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.message()).thenReturn(Component.text(line));
        // MockBukkit's callEvent reads getHandlers() to find registered listeners; a bare mock returns null otherwise.
        when(event.getHandlers()).thenReturn(AsyncChatEvent.getHandlerList());
        server.getPluginManager().callEvent(event);
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
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

    private static final class SyncScheduler implements Scheduler {
        private final List<Runnable> ignored = new ArrayList<>();

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
            ignored.add(task);
        }
    }
}
