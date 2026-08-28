package com.uxplima.uxmessentials.customcommands;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.customcommands.adapter.CustomCommandLoader;
import com.uxplima.uxmessentials.customcommands.adapter.inbound.command.CreateWizard;
import com.uxplima.uxmessentials.customcommands.adapter.inbound.command.WizardPrompt;
import com.uxplima.uxmessentials.customcommands.domain.ActionChain;
import com.uxplima.uxmessentials.customcommands.domain.ArgumentKind;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The wizard's answers, scripted. What matters is that the file it writes parses back into the definition the
 * answers describe, that an id somebody already used is refused before the first question, that a bad answer costs
 * one question rather than the whole run, and that cancelling writes nothing.
 */
class CreateWizardTest {

    private static final ActionChain.ChainLimits LIMITS = ActionChain.ChainLimits.defaults();

    @TempDir
    Path directory;

    private ServerMock server;
    private PlayerMock player;
    private PlayerRef viewer;
    private ScriptedPrompt prompt;
    private final List<String> saved = new ArrayList<>();
    private Set<String> taken = Set.of();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Operator");
        viewer = BukkitRefs.toRef(player);
        prompt = new ScriptedPrompt();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theAnswersBecomeAFileTheLoaderReadsBack() {
        prompt.script(
                "selamla", // the command word
                "hi merhaba", // aliases
                "tr:selam", // a Turkish-only alias
                "keep", // the suggested permission node
                "no", // the console may not run it
                "target online-player", // one argument
                "reason string optional", // one optional argument
                "done",
                "message:<green>hello %arg_target%",
                "broadcast:%player% said hello",
                "done",
                "save");

        assertThat(wizard().start(player, viewer, "selam")).isTrue();

        CustomCommand written = loaded("selam");
        assertThat(written.literal().name()).isEqualTo("selamla");
        assertThat(written.literal().aliases()).containsExactly("hi", "merhaba");
        assertThat(written.literal().localizedAliases()).containsEntry("tr", List.of("selam"));
        assertThat(written.permission()).contains("uxmessentials.customcommand.selam");
        assertThat(written.consoleAllowed()).isFalse();
        assertThat(written.arguments()).hasSize(2);
        assertThat(written.arguments().get(0).kind()).isEqualTo(ArgumentKind.ONLINE_PLAYER);
        assertThat(written.arguments().get(1).optional()).isTrue();
        assertThat(written.actions().steps()).hasSize(2);
        assertThat(saved).containsExactly("selam");
    }

    @Test
    void anIdAlreadyInUseIsRefusedBeforeTheFirstQuestion() {
        taken = Set.of("selam");

        assertThat(wizard().start(player, viewer, "selam")).isFalse();
        assertThat(prompt.asked()).isEmpty();
        assertThat(directory.toFile().list()).isNullOrEmpty();
    }

    @Test
    void anIdThatIsNotAUsableFileNameIsRefusedToo() {
        assertThat(wizard().start(player, viewer, "Not A Command")).isFalse();
        assertThat(prompt.asked()).isEmpty();
    }

    @Test
    void aBadAnswerCostsOneQuestionRatherThanTheWholeRun() {
        prompt.script(
                "not a word", // rejected: re-asks the name
                "selamla",
                "none",
                "none",
                "none",
                "maybe", // rejected: re-asks the console question
                "yes",
                "done",
                "message:hello",
                "done",
                "save");

        assertThat(wizard().start(player, viewer, "selam")).isTrue();

        assertThat(loaded("selam").literal().name()).isEqualTo("selamla");
        assertThat(prompt.asked())
                .filteredOn("customcommand.wizard.name"::equals)
                .hasSize(2);
        assertThat(prompt.asked())
                .filteredOn("customcommand.wizard.console"::equals)
                .hasSize(2);
    }

    @Test
    void cancellingAtTheLastStepWritesNothing() {
        prompt.script("selamla", "none", "none", "none", "yes", "done", "message:hello", "done", "no thanks");

        wizard().start(player, viewer, "selam");

        assertThat(Files.exists(directory.resolve("selam.conf"))).isFalse();
        assertThat(saved).isEmpty();
    }

    @Test
    void cancellingMidWayWritesNothing() {
        prompt.cancelAfter(3);
        prompt.script("selamla", "none", "none");

        wizard().start(player, viewer, "selam");

        assertThat(Files.exists(directory.resolve("selam.conf"))).isFalse();
        assertThat(saved).isEmpty();
    }

    private CreateWizard wizard() {
        return new CreateWizard(
                prompt, directory, () -> taken, new CommandFeedback(new KeyMessages()), saved::add, new SilentLogger());
    }

    private CustomCommand loaded(String id) {
        return new CustomCommandLoader(new SilentLogger())
                .loadFrom(directory, LIMITS)
                .catalog()
                .byId(id)
                .orElseThrow();
    }

    /** Answers the questions from a queue, recording each input-point key it was asked under. */
    private static final class ScriptedPrompt implements WizardPrompt {

        private final Deque<String> answers = new ArrayDeque<>();
        private final List<String> asked = new ArrayList<>();
        private int cancelAfter = Integer.MAX_VALUE;

        void script(String... lines) {
            answers.addAll(List.of(lines));
        }

        /** Cancel once this many questions have been answered, which is what closing the prompt does. */
        void cancelAfter(int questions) {
            cancelAfter = questions;
        }

        List<String> asked() {
            return asked;
        }

        @Override
        public void ask(
                Player player, PlayerRef viewer, InputRequest request, Consumer<String> onSubmit, Runnable onCancel) {
            asked.add(request.key());
            if (asked.size() > cancelAfter || answers.isEmpty()) {
                onCancel.run();
                return;
            }
            onSubmit.accept(answers.removeFirst());
        }
    }

    private static final class KeyMessages implements Messages {

        @Override
        public String resolve(PlayerRef viewer, MessageKey lookup, Map<String, String> placeholders) {
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
