package com.uxplima.uxmessentials.persistence.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The doctor names the schema version the database is really on.
 *
 * <p>It read the largest version as text, and as text "9" sorts after "85": a live server on V85 reported
 * {@code schema v9}, which reads as a database eighty migrations behind.
 */
class PersistenceProbeTest {

    private Persistence persistence;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    @DisplayName("the probe reports the last migration applied, compared as a version and not as text")
    void theProbeReportsTheLatestMigration() throws URISyntaxException {
        assertThat(persistence.probe().schemaVersion()).contains(Integer.toString(latestShippedMigration()));
    }

    private static int latestShippedMigration() throws URISyntaxException {
        Path folder = Path.of(Objects.requireNonNull(
                        PersistenceProbeTest.class.getClassLoader().getResource("db/migration"), "db/migration")
                .toURI());
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString())
                    .filter(name -> name.matches("V\\d+__.*\\.sql"))
                    .mapToInt(name -> Integer.parseInt(name.substring(1, name.indexOf("__"))))
                    .max()
                    .orElseThrow();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record SqliteConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    private static final class NoopLogger implements Logger {
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
