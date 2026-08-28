package com.uxplima.uxmessentials.customcommands;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.customcommands.adapter.CustomCommandLoader;
import com.uxplima.uxmessentials.customcommands.adapter.CustomCommandWriter;
import com.uxplima.uxmessentials.customcommands.domain.ActionChain;
import com.uxplima.uxmessentials.customcommands.domain.ArgumentKind;
import com.uxplima.uxmessentials.customcommands.domain.CommandArgument;
import com.uxplima.uxmessentials.customcommands.domain.CommandLiteral;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommandId;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The writer's contract is the loader's inverse: whatever it emits, the loader has to read back as the same
 * definition. That round trip is the test that matters, because the in-game wizard saves through this class and an
 * operator will open the file afterwards to hand-edit it.
 */
class CustomCommandWriterTest {

    /** The one character the house style bans outright, spelled as an escape so the file itself stays clean. */
    private static final String EM_DASH = "\u2014";

    private static final ActionChain.ChainLimits LIMITS = ActionChain.ChainLimits.defaults();

    private final CustomCommandLoader loader = new CustomCommandLoader(new SilentLogger());

    @Test
    void whatTheWriterEmitsTheLoaderReadsBackUnchanged(@TempDir Path directory) throws IOException {
        CustomCommand original = fullyPopulatedDefinition();

        CustomCommandWriter.write(directory, original);
        CustomCommand reloaded = loader.loadFrom(directory, LIMITS)
                .catalog()
                .byId(original.id().value())
                .orElseThrow();

        assertThat(reloaded).isEqualTo(original);
    }

    @Test
    void aBareDefinitionRoundTripsToo(@TempDir Path directory) throws IOException {
        CustomCommand original = minimalDefinition();

        CustomCommandWriter.write(directory, original);
        CustomCommand reloaded = loader.loadFrom(directory, LIMITS)
                .catalog()
                .byId(original.id().value())
                .orElseThrow();

        assertThat(reloaded).isEqualTo(original);
    }

    @Test
    void aDefinitionWithNoOptionalFieldsEmitsNoEmptyBlocks() {
        String rendered = CustomCommandWriter.render(minimalDefinition());

        assertThat(rendered)
                .doesNotContain("arguments")
                .doesNotContain("requirements")
                .doesNotContain("deny-message")
                .doesNotContain("cooldown")
                .doesNotContain("cost");
    }

    @Test
    void aDelayedChainWritesTheDelayTokensBackWhereTheyWere() {
        String rendered = CustomCommandWriter.render(fullyPopulatedDefinition());

        assertThat(rendered).contains("delay:2s");
    }

    @Test
    void theEmittedFileCarriesNoEmDashAndNoTrailingWhitespace() {
        String rendered = CustomCommandWriter.render(fullyPopulatedDefinition());

        assertThat(rendered).doesNotContain(EM_DASH);
        assertThat(rendered.lines()).allSatisfy(line -> assertThat(line).isEqualTo(line.stripTrailing()));
    }

    /** Every optional field set, so nothing the loader reads goes untested by the round trip. */
    private static CustomCommand fullyPopulatedDefinition() {
        return new CustomCommand(
                CustomCommandId.of("odul"),
                new CommandLiteral(
                        "odul", List.of("reward"), Map.of("tr", List.of("odulver"), "de", List.of("belohnung"))),
                Optional.of("uxmessentials.customcommand.odul"),
                Optional.of("<red>This command is not for you."),
                true,
                "Reward a player",
                Optional.of("/odul <target> <amount> [reason]"),
                Duration.ofSeconds(30),
                Duration.ofSeconds(3),
                100,
                List.of(
                        CommandArgument.of("target", ArgumentKind.ONLINE_PLAYER),
                        new CommandArgument(
                                "amount", ArgumentKind.INT, false, false, Optional.of(1.0), Optional.of(64.0)),
                        new CommandArgument(
                                "reason", ArgumentKind.STRING, true, true, Optional.empty(), Optional.empty())),
                List.of("has-money:100"),
                ActionChain.of(List.of("message:<red>You need 100 coins."), LIMITS),
                ActionChain.of(
                        List.of(
                                "take-money:100",
                                "console:give %arg_target% diamond %arg_amount%",
                                "delay:2s",
                                "broadcast:<gold>%player% handed out a reward."),
                        LIMITS));
    }

    /** The least a definition can carry: an id, a name, a description and one step. */
    private static CustomCommand minimalDefinition() {
        return new CustomCommand(
                CustomCommandId.of("selam"),
                CommandLiteral.of("selam"),
                Optional.empty(),
                Optional.empty(),
                true,
                "Say hello",
                Optional.empty(),
                Duration.ZERO,
                Duration.ZERO,
                0,
                List.of(),
                List.of(),
                ActionChain.empty(),
                ActionChain.of(List.of("message:hello"), LIMITS));
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
