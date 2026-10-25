package com.uxplima.uxmessentials.persistence.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The storage block is spelled the way every plugin of the family spells it: {@code type} and {@code pool-size}. A
 * file written before that names them {@code backend} and {@code read-pool-size}, and nothing rewrites an operator's
 * file, so the old names are still read when the new ones are not written. A network that kept its old file must
 * not wake up on a SQLite file.
 */
final class DatabaseSettingsTest {

    private static final Path FOLDER = Path.of("plugins", "uxmEssentials");

    @Test
    @DisplayName("type and pool-size are the names the storage block is read by")
    void theFamilyNamesAreRead() {
        DatabaseSettings settings = settings(Map.of("storage.type", "postgresql", "storage.pool-size", "12"));

        assertThat(settings.backend()).isEqualTo(DatabaseBackend.POSTGRES);
        assertThat(settings.readPoolSize()).isEqualTo(12);
    }

    @Test
    @DisplayName("a file that still says backend and read-pool-size keeps its database")
    void theOldNamesStillCount() {
        DatabaseSettings settings = settings(Map.of("storage.backend", "mysql", "storage.read-pool-size", "6"));

        assertThat(settings.backend()).isEqualTo(DatabaseBackend.MARIADB);
        assertThat(settings.readPoolSize()).isEqualTo(6);
    }

    @Test
    @DisplayName("the new name wins when a file writes both")
    void theNewNameWins() {
        DatabaseSettings settings = settings(Map.of(
                "storage.type", "mariadb",
                "storage.backend", "mysql",
                "storage.pool-size", "3",
                "storage.read-pool-size", "9"));

        assertThat(settings.backend()).isEqualTo(DatabaseBackend.MARIADB);
        assertThat(settings.readPoolSize()).isEqualTo(3);
    }

    @Test
    @DisplayName("a file that names nothing is the SQLite file, and a port of zero is the backend's own")
    void nothingIsSqlite() {
        assertThat(settings(Map.of()).backend()).isEqualTo(DatabaseBackend.SQLITE);
        assertThat(settings(Map.of("storage.type", "postgresql", "storage.port", "0"))
                        .port())
                .isEqualTo(5432);
        assertThat(settings(Map.of("storage.type", "mysql", "storage.port", "0"))
                        .port())
                .isEqualTo(3306);
        assertThat(settings(Map.of("storage.type", "mysql", "storage.port", "3307"))
                        .port())
                .isEqualTo(3307);
    }

    @Test
    @DisplayName("local-only keeps this one server on its SQLite file, whatever the block names")
    void localOnlyKeepsTheServerOnItsFile() {
        DatabaseSettings settings =
                settings(Map.of("storage.type", "postgresql", "storage.host", "network", "storage.local-only", "true"));

        assertThat(settings.backend()).isEqualTo(DatabaseBackend.SQLITE);
        assertThat(settings(Map.of("storage.type", "postgresql", "storage.local-only", "false"))
                        .backend())
                .isEqualTo(DatabaseBackend.POSTGRES);
    }

    @Test
    @DisplayName(
            "a word that names no database stops a new install, which would otherwise start apart from its network")
    void anUnknownWordStopsANewInstall(@TempDir Path folder) {
        DatabaseSettings settings = new DatabaseSettings(new Written(Map.of("storage.backend", "sqlite3")), folder);

        assertThatThrownBy(settings::backend).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a server that ran on its SQLite file under a word that names no database keeps running on it")
    void anUnknownWordKeepsAServerThatRanOnTheFile(@TempDir Path folder) throws IOException {
        // Before 0.10.1 such a word fell back to SQLite, so a server written so has its rows in the file. Stopping
        // it on the upgrade would take a working server down for a word it always had.
        Files.createFile(folder.resolve("uxmessentials.db"));
        DatabaseSettings settings = new DatabaseSettings(new Written(Map.of("storage.backend", "sqlite3")), folder);

        assertThat(settings.backend()).isEqualTo(DatabaseBackend.SQLITE);
        assertThat(settings.unknownType()).contains("sqlite3");
        assertThat(new DatabaseSettings(new Written(Map.of("storage.type", "mysql")), folder).unknownType())
                .isEmpty();
    }

    private static DatabaseSettings settings(Map<String, String> values) {
        return new DatabaseSettings(new Written(values), FOLDER);
    }

    /** A config that holds what a test wrote and nothing else. */
    private record Written(Map<String, String> values) implements ConfigStore {

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            String value = values.get(path);
            return value == null ? fallback : Boolean.parseBoolean(value);
        }

        @Override
        public String getString(String path, String fallback) {
            return values.getOrDefault(path, fallback);
        }

        @Override
        public int getInt(String path, int fallback) {
            String value = values.get(path);
            return value == null ? fallback : Integer.parseInt(value);
        }
    }
}
