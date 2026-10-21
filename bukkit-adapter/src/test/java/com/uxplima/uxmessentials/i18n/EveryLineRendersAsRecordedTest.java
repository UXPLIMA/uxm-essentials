package com.uxplima.uxmessentials.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.outbound.message.HoconLocaleCatalog;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKeyCatalog;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every line of every shipped catalogue renders to the component it rendered to when this file was recorded.
 *
 * <p>The record is a hash of each line's component, as a client would be sent it, in each of the twelve languages.
 * It exists so the text pipeline can be moved without a player seeing a difference: a line that renders differently
 * names its key and its language, and the new component is printed so the change can be judged rather than guessed.
 * A change that is meant is recorded again with {@code RECORD_RENDERED_LINES=true} in the environment, and the diff of the record
 * shows exactly which lines it touched.
 */
final class EveryLineRendersAsRecordedTest {

    private static final String RECORD = "i18n/rendered-lines.txt";
    private static final Path RECORD_SOURCE = Path.of("src/test/resources", RECORD);
    private static final MessageKey PREFIX = () -> "prefix";

    @TempDir
    Path noOperatorFiles;

    @Test
    void everyLineRendersAsRecorded() throws IOException {
        HoconLocaleCatalog catalog = new HoconLocaleCatalog(new QuietLogger(), noOperatorFiles);
        List<String> languages =
                CatalogKeys.shippedLanguages().stream().sorted().toList();
        Map<String, String> rendered = new TreeMap<>();
        Map<String, String> json = new LinkedHashMap<>();
        for (MessageKey key : MessageKeyCatalog.all()) {
            StringBuilder line = new StringBuilder();
            for (String language : languages) {
                String component = render(catalog, key, Locale.forLanguageTag(language));
                json.put(language + " " + key.key(), component);
                line.append(' ').append(hash(component));
            }
            rendered.put(key.key(), line.toString());
        }
        List<String> lines = new ArrayList<>();
        lines.add("# languages:" + String.join(" ", languages));
        rendered.forEach((key, hashes) -> lines.add(key + hashes));

        if ("true".equals(System.getenv("RECORD_RENDERED_LINES"))) {
            Files.createDirectories(RECORD_SOURCE.getParent());
            Files.write(RECORD_SOURCE, lines, StandardCharsets.UTF_8);
            return;
        }
        List<String> recorded = recorded();
        assertThat(recorded).as("the record exists and is not empty").hasSizeGreaterThan(1000);
        assertThat(lines.get(0)).as("the languages the record was made in").isEqualTo(recorded.get(0));
        List<String> changed = changes(recorded, lines, languages, json);
        assertThat(changed).as("lines that no longer render as recorded").isEmpty();
    }

    private static String render(HoconLocaleCatalog catalog, MessageKey key, Locale locale) {
        String prefix = catalog.template(Locale.ENGLISH, PREFIX);
        Component component = MiniMessage.miniMessage()
                .deserialize(catalog.template(locale, key), Placeholder.parsed("prefix", prefix), StyleTags.resolver());
        return GsonComponentSerializer.gson().serialize(component);
    }

    /** Each recorded line that differs now: its key, the languages that changed, and what they render to now. */
    private static List<String> changes(
            List<String> recorded, List<String> now, List<String> languages, Map<String, String> json) {
        Map<String, String> before = byKey(recorded);
        Map<String, String> after = byKey(now);
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, String> entry : after.entrySet()) {
            String was = before.get(entry.getKey());
            if (was == null) {
                changed.add(entry.getKey() + ": not in the record");
                continue;
            }
            List<String> oldHashes = List.of(was.trim().split(" ", -1));
            List<String> newHashes = List.of(entry.getValue().trim().split(" ", -1));
            for (int i = 0; i < languages.size(); i++) {
                if (!oldHashes.get(i).equals(newHashes.get(i))) {
                    changed.add(entry.getKey() + " [" + languages.get(i) + "]: "
                            + json.get(languages.get(i) + " " + entry.getKey()));
                }
            }
        }
        before.keySet().stream()
                .filter(key -> !after.containsKey(key))
                .forEach(key -> changed.add(key + ": recorded, no longer a key"));
        return changed;
    }

    private static Map<String, String> byKey(List<String> lines) {
        Map<String, String> byKey = new TreeMap<>();
        for (String line : lines.subList(1, lines.size())) {
            int space = line.indexOf(' ');
            byKey.put(line.substring(0, space), line.substring(space));
        }
        return byKey;
    }

    private static List<String> recorded() throws IOException {
        try (InputStream in = Objects.requireNonNull(
                EveryLineRendersAsRecordedTest.class.getClassLoader().getResourceAsStream(RECORD),
                "no record at " + RECORD + "; make it with RECORD_RENDERED_LINES=true")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }

    /** Eight hex digits of the line's SHA-256: enough to tell two renderings apart, small enough to keep. */
    private static String hash(String component) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(component.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class QuietLogger implements Logger {

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
