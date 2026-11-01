package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.adapter.outbound.config.ConfigurateConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The pickers' presets name a line of the messages files, and every language ships that line. */
class WarpPresetsTest {

    private static final List<String> LANGUAGES =
            List.of("en", "tr", "de", "es", "fr", "ja", "ko", "pl", "pt", "ru", "uk", "zh");

    private static final Logger QUIET = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };

    @TempDir
    Path folder;

    private WarpPresets.Name name(String written) throws IOException {
        Path file = folder.resolve("warps.conf");
        Files.writeString(file, "preset { " + written + " }\n");
        return WarpPresets.name(ConfigurateConfigStore.load(file, QUIET), "preset.name");
    }

    @Test
    void everyShippedPresetNamesALineThatEveryLanguageWrites() throws IOException {
        String warps = resource("/modules/warps/config.conf");
        List<String> lines = Pattern.compile("name = \"@([^\"]+)\"")
                .matcher(warps)
                .results()
                .map(found -> found.group(1))
                .toList();

        assertThat(lines).hasSize(28).doesNotHaveDuplicates();
        assertThat(warps).doesNotContain("name {");
        for (String language : LANGUAGES) {
            String messages = resource("/messages/messages_" + language + ".conf");
            assertThat(lines)
                    .as("lines missing from messages_" + language)
                    .allSatisfy(line -> assertThat(messages).as(line).contains("\"" + line + "\" = \""));
        }
    }

    @Test
    void aNameThatPointsAtALineIsThatLine() throws IOException {
        WarpPresets.Name name = name("name = \"@content.warps.particle.flame\"");

        assertThat(name.line()).isEqualTo("content.warps.particle.flame");
        assertThat(name.byLanguage()).isEmpty();
    }

    @Test
    void aNameWrittenPerLanguageStillReadsInEachLanguage() throws IOException {
        WarpPresets.Name name = name("name { en = \"Flame\", tr = \"Alev\" }");
        WarpPresets.Preset preset = new WarpPresets.Preset("minecraft:flame", Material.STONE, name.byLanguage());

        assertThat(name.line()).isEmpty();
        assertThat(preset.nameIn("tr")).isEqualTo("Alev");
        assertThat(preset.nameIn("de"))
                .as("a language the block does not write")
                .isEqualTo("Flame");
    }

    @Test
    void aPlainNameIsReadByEveryLanguage() throws IOException {
        WarpPresets.Name name = name("name = \"Flame\"");
        WarpPresets.Preset preset = new WarpPresets.Preset("minecraft:flame", Material.STONE, name.byLanguage());

        assertThat(preset.nameIn("tr")).isEqualTo("Flame");
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = Objects.requireNonNull(WarpPresetsTest.class.getResourceAsStream(name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
