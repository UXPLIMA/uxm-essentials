package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

/** The plugin starting the ways an operator starts it: fresh, after editing a file, and on a file an old release wrote. */
@Tag("integration")
class PluginStartScenarioTest {

    @Test
    void aFreshServerStartsEveryShippedModuleWithoutAWarning() {
        try (RunningPlugin running = RunningPlugin.start()) {
            assertThat(running.plugin.isEnabled()).isTrue();
            assertThat(running.problems())
                    .as("warnings and errors on a fresh start")
                    .isEmpty();
            assertThat(running.api().economy())
                    .as("the economy reached through the developer API")
                    .isPresent();
        }
    }

    @Test
    void aReloadShowsAMessageTheOperatorRewroteWhileTheServerRan() throws IOException {
        try (RunningPlugin running = RunningPlugin.start()) {
            ScenarioPlayer operator = running.join("Olive");
            operator.setOp(true);
            RunningPlugin.said(operator);
            running.run(operator, "balance");
            List<String> before = RunningPlugin.said(operator);

            Path english = running.dataFolder().resolve("messages/messages_en.conf");
            String rewritten = Files.readString(english)
                    .replace(
                            "\"wallet.balance\" = \"<tag:'ECONOMY'> balance: <money>{amount}</money>.\"",
                            "\"wallet.balance\" = \"<money>{amount}</money> #4242\"");
            Files.writeString(english, rewritten);
            running.run(operator, "uxmess reload");
            List<String> report = RunningPlugin.said(operator);
            running.run(operator, "balance");
            List<String> after = RunningPlugin.said(operator);

            assertThat(before).singleElement().asString().doesNotContain("#4242");
            assertThat(after)
                    .as("the balance line after a reload that reported %s", report)
                    .singleElement()
                    .asString()
                    .contains("#4242");
            assertThat(running.problems()).isEmpty();
        }
    }

    @Test
    void aConfigurationAnEarlyReleaseWroteStartsAndKeepsTheOperatorsLanguage() throws IOException {
        String shipped = resource("scenario/release-0.5.1/config.conf");
        String edited = shipped.replace("default-locale = \"en\"", "default-locale = \"de\"");
        assertThat(edited).as("the operator's edit").isNotEqualTo(shipped);
        // The shipped copy beside it is what that release kept to tell the operator's edits from its own defaults.
        try (RunningPlugin running =
                RunningPlugin.start(Map.of("config.conf", edited, ".defaults/config.conf", shipped))) {
            assertThat(running.problems())
                    .as("warnings and errors on the old file")
                    .isEmpty();
            String upgraded = Files.readString(running.dataFolder().resolve("config.conf"));
            assertThat(upgraded)
                    .as("the operator's own lines stay and the settings added since are appended")
                    .contains("default-locale = \"de\"")
                    .contains("language {");

            ScenarioPlayer german = joinReading(running, "Greta", Locale.GERMANY);
            ScenarioPlayer english = joinReading(running, "Edith", Locale.US);
            List<String> germanLines = statusLines(running, german);

            running.console("uxmess status");
            List<String> consoleLines = said(running.server.getConsoleSender());

            assertThat(consoleLines)
                    .as("the console reads the server's default, which the old file names German")
                    .isEqualTo(germanLines)
                    .isNotEqualTo(statusLines(running, english));
        }
    }

    private static ScenarioPlayer joinReading(RunningPlugin running, String name, Locale locale) {
        ScenarioPlayer player = new ScenarioPlayer(running.server, name);
        player.setLocale(locale);
        running.server.addPlayer(player);
        running.settle();
        RunningPlugin.said(player);
        return player;
    }

    private static List<String> statusLines(RunningPlugin running, ScenarioPlayer player) {
        player.setOp(true);
        running.run(player, "uxmess status");
        return RunningPlugin.said(player);
    }

    private static List<String> said(ConsoleCommandSenderMock console) {
        List<String> lines = new ArrayList<>();
        Component line;
        while ((line = console.nextComponentMessage()) != null) {
            lines.add(PlainTextComponentSerializer.plainText().serialize(line));
        }
        return lines;
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                PluginStartScenarioTest.class.getClassLoader().getResourceAsStream(path), path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
