package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.ThemeFile;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which languages a catalogue is written in: the shipped ones and the files the operator adds, not merely asked ones. */
class HoconLocaleCatalogCoverageTest {

    private static final Locale ESPERANTO = Locale.forLanguageTag("eo");

    @TempDir
    Path messages;

    private HoconLocaleCatalog catalog() {
        return new HoconLocaleCatalog(new Silent(), messages, ThemeFile::shippedTheme);
    }

    @Test
    void aShippedLanguageIsCoveredAndOneNobodyWroteIsNot() {
        HoconLocaleCatalog catalog = catalog();

        assertThat(catalog.covers(Locale.GERMANY)).isTrue();
        assertThat(catalog.covers(Locale.ENGLISH)).isTrue();
        assertThat(catalog.covers(ESPERANTO)).isFalse();
        assertThat(catalog.covers(Locale.ROOT)).isFalse();
    }

    @Test
    void readingALanguageWithNoFileDoesNotMakeItCovered() {
        HoconLocaleCatalog catalog = catalog();

        catalog.template(ESPERANTO, () -> "lang.current");

        assertThat(catalog.covers(ESPERANTO)).isFalse();
    }

    @Test
    void aFileTheOperatorAddsCountsAfterAReload() throws IOException {
        HoconLocaleCatalog catalog = catalog();
        assertThat(catalog.covers(ESPERANTO)).isFalse();

        Files.writeString(messages.resolve("messages_eo.conf"), "\"lang.current\" = \"Saluton\"\n");
        catalog.reload();

        assertThat(catalog.covers(ESPERANTO)).isTrue();
    }

    private static final class Silent implements Logger {

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
