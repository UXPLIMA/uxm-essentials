package com.uxplima.uxmessentials.bootstrap.di;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.uxplima.uxmessentials.shared.adapter.outbound.config.ConfigurateConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.text.language.LanguageSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code language} block every plugin of ours reads, read here too, without losing the language a server
 * chose before the block existed.
 *
 * <p>uxmEssentials issue 2 asked for it: a German-only server whose players' clients read English got English,
 * because the client came before the server's own default and nothing turned that off.
 */
class LanguageSettingsReadTest {

    @Test
    @DisplayName("the three keys are read")
    void theBlockIsRead(@TempDir Path folder) throws IOException {
        LanguageSettings read = read(folder, """
                language { default = "de", follow-client = false, force = "" }
                """);

        assertThat(read.defaultLocale()).isEqualTo(Locale.GERMAN);
        assertThat(read.followClient()).isFalse();
        assertThat(read.forcedLocale()).isEmpty();
    }

    @Test
    @DisplayName("a forced language is read")
    void aForcedLanguageIsRead(@TempDir Path folder) throws IOException {
        assertThat(read(folder, "language { force = \"tr\" }\n").forcedLocale()).contains(Locale.forLanguageTag("tr"));
    }

    @Test
    @DisplayName("a file from before the block keeps its default")
    void theOldKeyIsStillRead(@TempDir Path folder) throws IOException {
        LanguageSettings read = read(folder, "messages { default-locale = \"de\" }\n");

        assertThat(read.defaultLocale()).isEqualTo(Locale.GERMAN);
        assertThat(read.followClient()).isTrue();
    }

    @Test
    @DisplayName("the block an update appends does not undo the default the server chose before it")
    void anAppendedBlockKeepsTheOldDefault(@TempDir Path folder) throws IOException {
        LanguageSettings read = read(folder, """
                messages { default-locale = "de" }
                language { default = "en", follow-client = true, force = "" }
                """);

        assertThat(read.defaultLocale()).isEqualTo(Locale.GERMAN);
        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("a default an operator wrote in the block wins over the old key, and the old key is named")
    void theBlockWinsOnceWritten(@TempDir Path folder) throws IOException {
        LanguageSettings read = read(folder, """
                messages { default-locale = "de" }
                language { default = "tr" }
                """);

        assertThat(read.defaultLocale()).isEqualTo(Locale.forLanguageTag("tr"));
        assertThat(warnings).singleElement().asString().contains("messages.default-locale");
    }

    @Test
    @DisplayName("a file with neither reads English and follows the client")
    void nothingWrittenIsEnglish(@TempDir Path folder) throws IOException {
        LanguageSettings read = read(folder, "storage { backend = \"sqlite\" }\n");

        assertThat(read.defaultLocale()).isEqualTo(Locale.ENGLISH);
        assertThat(read.followClient()).isTrue();
        assertThat(read.forcedLocale()).isEmpty();
    }

    private final List<String> warnings = new ArrayList<>();

    private LanguageSettings read(Path folder, String body) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, body, StandardCharsets.UTF_8);
        Logger log = new Logger() {
            @Override
            public void info(String message, Object... args) {}

            @Override
            public void warn(String message, Object... args) {
                warnings.add(message);
            }

            @Override
            public void error(String message, Throwable cause) {}

            @Override
            public void debug(String message, Object... args) {}
        };
        return KernelWiring.languageSettings(ConfigurateConfigStore.load(file, log), log);
    }
}
