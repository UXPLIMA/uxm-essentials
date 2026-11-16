package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A player whose client is set to a language the server has no messages for, or reports none, reads the server's
 * default language, whether the operator named it in the language block or in the line an early release wrote.
 */
@Tag("integration")
class ClientLanguageScenarioTest {

    private static final Locale ESPERANTO = Locale.forLanguageTag("eo");

    @Test
    void aClientWithNoCatalogueReadsTheDefaultTheLanguageBlockNames() throws IOException {
        String shipped = resource("config.conf");
        String german = shipped.replace("default = \"en\"", "default = \"de\"");
        assertThat(german).as("the operator's edit").isNotEqualTo(shipped);

        try (RunningPlugin running = RunningPlugin.start(Map.of("config.conf", german))) {
            assertReadsTheDefault(running);
        }
    }

    @Test
    void aClientWithNoCatalogueReadsTheDefaultAnEarlyReleaseWrote() throws IOException {
        String shipped = resource("scenario/release-0.5.1/config.conf");
        String german = shipped.replace("default-locale = \"en\"", "default-locale = \"de\"");
        assertThat(german).as("the operator's edit").isNotEqualTo(shipped);

        try (RunningPlugin running =
                RunningPlugin.start(Map.of("config.conf", german, ".defaults/config.conf", shipped))) {
            assertReadsTheDefault(running);
        }
    }

    @Test
    void aClientWithNoCatalogueReadsEnglishWhereEnglishIsTheDefault() {
        try (RunningPlugin running = RunningPlugin.start()) {
            List<String> english = statusLines(running, "Edith", Locale.US);
            List<String> german = statusLines(running, "Greta", Locale.GERMANY);

            assertThat(statusLines(running, "Esme", ESPERANTO))
                    .isEqualTo(english)
                    .isNotEqualTo(german);
            assertThat(running.problems()).isEmpty();
        }
    }

    private static void assertReadsTheDefault(RunningPlugin running) {
        List<String> german = statusLines(running, "Greta", Locale.GERMANY);
        List<String> english = statusLines(running, "Edith", Locale.US);
        assertThat(german).as("the default and English read differently").isNotEqualTo(english);

        assertThat(statusLines(running, "Esme", ESPERANTO))
                .as("a client set to a language with no messages file")
                .isEqualTo(german);
        assertThat(statusLines(running, "Nils", Locale.ROOT))
                .as("a client that reports no language")
                .isEqualTo(german);
        assertThat(running.problems()).isEmpty();
    }

    /** What {@code /uxmess status} tells an operator whose client reads {@code locale}. */
    private static List<String> statusLines(RunningPlugin running, String name, Locale locale) {
        ScenarioPlayer player = new ScenarioPlayer(running.server, name);
        player.setLocale(locale);
        running.server.addPlayer(player);
        running.settle();
        player.setOp(true);
        RunningPlugin.said(player);
        running.run(player, "uxmess status");
        return RunningPlugin.said(player);
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                ClientLanguageScenarioTest.class.getClassLoader().getResourceAsStream(path), path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
