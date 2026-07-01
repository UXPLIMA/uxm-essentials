package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock.BedrockDetector;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock.BedrockScreen;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The proof that the text-input seam renders a Bedrock viewer's prompt as a native Cumulus CustomForm: a real
 * {@link TextInput} with a fake {@link BedrockDetector} / {@link BedrockScreen} (standing in for the Cumulus/Floodgate
 * SDK, a {@code compileOnly} soft-depend absent from the test runtime) sends a Bedrock viewer a single-input form
 * instead of the chat/anvil prompt. The typed value and the form's close both flow through the same shared cancel
 * policy the anvil/chat backends use: a plain line runs {@code onSubmit}, a line matching a cancel keyword and a form
 * close both run {@code onCancel} with the {@code gui.input.cancelled} acknowledgement. A Java viewer keeps the chat
 * prompt byte-identically and the screen is never called.
 */
class BedrockInputFormTest {

    private static final MessageKey LABEL = GuiMessageKey.COLOUR_PICKER_CUSTOM_PROMPT;

    @TempDir
    Path dir;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private FakeBedrockDetector detector;
    private FakeBedrockScreen screen;
    private TextInput textInput;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        Files.writeString(dir.resolve("text-input.conf"), """
                default-mode = chat
                cancel-keywords = ["cancel"]
                """);
        detector = new FakeBedrockDetector();
        screen = new FakeBedrockScreen();
        textInput = build();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aBedrockViewerGetsACustomFormNotAChatPromptAndSubmitFlowsThrough() {
        detector.bedrock = true;
        AtomicReference<String> submitted = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        textInput.prompt(player, viewer, InputRequest.of("any.key", LABEL), submitted::set, () -> cancelled.set(true));

        assertThat(player.nextMessage())
                .as("a Bedrock viewer is redirected to a form, so no chat prompt is sent")
                .isNull();
        assertThat(screen.sent)
                .as("the Bedrock screen is asked to send an input form")
                .isTrue();
        assertThat(screen.title)
                .as("the form title is the prompt label as plain text")
                .isEqualTo(LABEL.key());
        assertThat(screen.inputLabel)
                .as("the single input's label is that same plain label")
                .isEqualTo(LABEL.key());

        screen.submit("HomeBase");

        assertThat(submitted.get()).isEqualTo("HomeBase");
        assertThat(cancelled.get()).isFalse();
    }

    @Test
    void aSubmittedCancelKeywordRoutesToCancelWithTheAcknowledgement() {
        detector.bedrock = true;
        AtomicReference<String> submitted = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        textInput.prompt(player, viewer, InputRequest.of("any.key", LABEL), submitted::set, () -> cancelled.set(true));
        screen.submit("cancel");

        assertThat(submitted.get())
                .as("a submitted cancel keyword is not an accepted value, so onSubmit never runs")
                .isNull();
        assertThat(cancelled.get())
                .as("the shared route policy turns a cancel keyword into a cancellation")
                .isTrue();
        assertThat(player.nextMessage())
                .as("the centralised gui.input.cancelled acknowledgement is sent by the seam")
                .contains(GuiMessageKey.INPUT_CANCELLED.key());
    }

    @Test
    void closingTheFormRoutesToCancel() {
        detector.bedrock = true;
        AtomicReference<String> submitted = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        textInput.prompt(player, viewer, InputRequest.of("any.key", LABEL), submitted::set, () -> cancelled.set(true));
        screen.close();

        assertThat(submitted.get()).as("closing the form submits nothing").isNull();
        assertThat(cancelled.get())
                .as("a form close resolves to a cancellation through the shared route policy")
                .isTrue();
        assertThat(player.nextMessage())
                .as("the cancel acknowledgement is sent on a close, exactly as an anvil close")
                .contains(GuiMessageKey.INPUT_CANCELLED.key());
    }

    @Test
    void aJavaViewerKeepsTheChatPromptAndTheScreenIsNeverCalled() {
        detector.bedrock = false;
        AtomicReference<String> submitted = new AtomicReference<>();

        textInput.prompt(player, viewer, InputRequest.of("any.key", LABEL), submitted::set, () -> {});

        assertThat(player.nextMessage())
                .as("a Java viewer opens the chat prompt exactly as before")
                .isNotNull();
        assertThat(screen.sent)
                .as("the Bedrock screen is never asked to send a form for a Java viewer")
                .isFalse();
    }

    private TextInput build() {
        InputSettings settings = new InputSettings(dir.resolve("text-input.conf"), new SilentLogger());
        GuiText guiText = new GuiText(new KeyMessages());
        AnvilTextBackend anvilBackend = new AnvilTextBackend(new AnvilInput(plugin));
        ChatTextBackend chatBackend = new ChatTextBackend(plugin);
        return new TextInput(settings, guiText, new SyncScheduler(), anvilBackend, chatBackend, detector, screen);
    }

    /** Records the last input form it was asked to send and lets the test fire the submit or the close callback. */
    private static final class FakeBedrockScreen implements BedrockScreen {
        private boolean sent;
        private @Nullable String title;
        private @Nullable String inputLabel;
        private @Nullable Consumer<String> onSubmit;
        private @Nullable Runnable onClose;

        @Override
        public void sendSimpleForm(
                Player player, String title, @Nullable String content, List<String> buttons, IntConsumer onSelect) {}

        @Override
        public void sendModalForm(
                Player player,
                String title,
                @Nullable String content,
                String button1,
                String button2,
                Runnable onButton1,
                Runnable onButton2) {}

        @Override
        public void sendInputForm(
                Player player,
                String title,
                String inputLabel,
                @Nullable String initial,
                Consumer<String> onSubmit,
                Runnable onClose) {
            this.sent = true;
            this.title = title;
            this.inputLabel = inputLabel;
            this.onSubmit = onSubmit;
            this.onClose = onClose;
        }

        void submit(String value) {
            if (onSubmit != null) {
                onSubmit.accept(value);
            }
        }

        void close() {
            if (onClose != null) {
                onClose.run();
            }
        }
    }

    /** A detector whose Bedrock answer the test flips per case; no Floodgate SDK involved. */
    private static final class FakeBedrockDetector implements BedrockDetector {
        private boolean bedrock;

        @Override
        public boolean isBedrock(UUID player) {
            return bedrock;
        }
    }

    /** Resolves a key to its own lookup string, so a rendered label is the {@code gui.*} key itself. */
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
        public void asyncAfter(Duration delay, Runnable task) {}
    }
}
