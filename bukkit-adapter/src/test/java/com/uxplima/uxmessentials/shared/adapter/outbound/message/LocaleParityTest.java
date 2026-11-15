package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.shared.application.message.MessageKeyCatalog;
import org.junit.jupiter.api.Test;

/** Every language writes every line, and every line the code or a shipped config names is written. */
class LocaleParityTest {

    private static final List<String> LANGUAGES =
            List.of("en", "de", "es", "fr", "ja", "ko", "pl", "pt", "ru", "tr", "uk", "zh");

    private static final Pattern ENTRY = Pattern.compile("(?m)^\"([^\"]+)\"\\s*=");
    /** A quoted value that opens with an at sign: a catalogue reference, alone or followed by its arguments. */
    private static final Pattern REFERENCE = Pattern.compile("\"@([A-Za-z0-9_.-]+)[\" ]");

    /** Configs whose shipped values name catalogue lines. */
    private static final List<String> REFERRING_CONFIGS = List.of(
            "/modules/vanish/config.conf",
            "/modules/staff/config.conf",
            "/modules/warps/config.conf",
            "/modules/security/config.conf",
            "/modules/communication/info-pages.conf",
            "/modules/communication/advancements.conf",
            "/modules/scoreboard/config.conf",
            "/modules/tablist/config.conf",
            "/modules/bossbar/config.conf");

    @Test
    void everyLanguageWritesTheSameLinesAsEnglish() throws IOException {
        Set<String> english = keys("en");

        for (String language : LANGUAGES) {
            assertThat(keys(language)).as("messages_" + language).isEqualTo(english);
        }
    }

    @Test
    void everyKeyTheCodeNamesIsWritten() throws IOException {
        Set<String> english = keys("en");

        assertThat(english).containsAll(MessageKeyCatalog.allKeys());
    }

    @Test
    void everyLineAShippedConfigPointsAtIsWritten() throws IOException {
        Set<String> english = keys("en");

        for (String config : REFERRING_CONFIGS) {
            for (String line : resource(config).lines().toList()) {
                if (line.strip().startsWith("#")) {
                    continue;
                }
                Matcher found = REFERENCE.matcher(line);
                while (found.find()) {
                    assertThat(english).as(config).contains(found.group(1));
                }
            }
        }
    }

    private static Set<String> keys(String language) throws IOException {
        Matcher found = ENTRY.matcher(resource("/messages/messages_" + language + ".conf"));
        Set<String> keys = new TreeSet<>();
        while (found.find()) {
            keys.add(found.group(1));
        }
        return keys;
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = Objects.requireNonNull(LocaleParityTest.class.getResourceAsStream(path), path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
