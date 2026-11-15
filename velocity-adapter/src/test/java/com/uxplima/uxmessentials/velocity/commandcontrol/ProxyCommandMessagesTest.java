package com.uxplima.uxmessentials.velocity.commandcontrol;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurateException;

/** The proxy's deny and spam lines read in each player's language unless an operator wrote their own. */
class ProxyCommandMessagesTest {

    private static final ProxyCatalog CATALOG = new ProxyCatalog(Map.of(
            "en", Map.of("proxy.command.no-permission", "<bad>No permission.</bad>"),
            "de", Map.of("proxy.command.no-permission", "<bad>Keine Berechtigung.</bad>")));

    @TempDir
    Path folder;

    @Test
    void theShippedLineReadsInThePlayersLanguage() {
        ProxyCommandMessages messages = ProxyCommandMessages.from(new Written(), CATALOG);

        assertThat(plain(messages.deny(false, Locale.GERMANY))).contains("Keine Berechtigung.");
        assertThat(plain(messages.deny(false, Locale.ENGLISH))).contains("No permission.");
    }

    @Test
    void aLanguageWithNoFileReadsEnglish() {
        ProxyCommandMessages messages = ProxyCommandMessages.from(new Written(), CATALOG);

        assertThat(plain(messages.deny(false, Locale.ITALIAN))).contains("No permission.");
        assertThat(plain(messages.deny(false, null))).contains("No permission.");
    }

    @Test
    void theEnglishLineEarlierVersionsShippedReadsTheCatalogueToo() {
        Written config =
                new Written().set("messages.no-permission", "<red>You do not have permission to use that command.");

        assertThat(plain(ProxyCommandMessages.from(config, CATALOG).deny(false, Locale.GERMANY)))
                .contains("Keine Berechtigung.");
    }

    @Test
    void aLineTheOperatorWroteIsSentAsWritten() {
        Written config = new Written().set("messages.no-permission", "<gray>Nope.");

        assertThat(plain(ProxyCommandMessages.from(config, CATALOG).deny(false, Locale.GERMANY)))
                .contains("Nope.");
    }

    @Test
    void aBlankLineSendsNothing() {
        Written config = new Written().set("messages.no-permission", "");

        assertThat(ProxyCommandMessages.from(config, CATALOG).deny(false, Locale.GERMANY))
                .isEmpty();
    }

    @Test
    void everyShippedLanguageWritesEveryLineTheConfigPointsAt() throws ConfigurateException {
        Map<String, String> english = ProxyCatalog.shipped("en");
        assertThat(english)
                .containsKeys(
                        key(ProxyCommandMessages.UNKNOWN_COMMAND),
                        key(ProxyCommandMessages.NO_PERMISSION),
                        key(ProxyCommandMessages.PLUGIN_HIDDEN),
                        key(ProxyCommandMessages.SPAM_KICK),
                        key(ProxyCommandMessages.SPAM_BLOCKED),
                        key(ProxyCommandMessages.SPAM_WARN));

        for (String language : ProxyCatalog.LANGUAGES) {
            assertThat(ProxyCatalog.shipped(language).keySet())
                    .as("messages_" + language)
                    .isEqualTo(english.keySet());
        }
    }

    @Test
    void anOperatorsFileWinsAndALineItLacksStillReads() throws IOException {
        Path messages = Files.createDirectories(folder.resolve("messages"));
        Files.writeString(messages.resolve("messages_de.conf"), "\"proxy.command.spam-warn\" = \"<warn>Langsam!\"\n");

        ProxyCatalog catalog = ProxyCatalog.load(folder);

        assertThat(catalog.line(Locale.GERMAN, "proxy.command.spam-warn")).isEqualTo("<warn>Langsam!");
        assertThat(catalog.line(Locale.GERMAN, "proxy.command.spam-kick"))
                .isEqualTo(ProxyCatalog.shipped("de").get("proxy.command.spam-kick"));
        assertThat(messages.resolve("messages_fr.conf")).exists();
    }

    private static String key(String reference) {
        return reference.substring(1);
    }

    private static String plain(Optional<Component> line) {
        StringBuilder text = new StringBuilder();
        line.ifPresent(component -> append(component, text));
        return text.toString();
    }

    private static void append(Component component, StringBuilder text) {
        if (component instanceof TextComponent words) {
            text.append(words.content());
        }
        component.children().forEach(child -> append(child, text));
    }

    /** The command-control block as an operator wrote it: the keys given here, every other key at its default. */
    private static final class Written implements ConfigStore {

        private final Map<String, String> values = new HashMap<>();

        Written set(String path, String value) {
            values.put(path, value);
            return this;
        }

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.getOrDefault(path, fallback);
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }
}
