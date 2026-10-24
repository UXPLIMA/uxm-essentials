package com.uxplima.uxmessentials.persistence.runtime;

import static com.uxplima.uxmessentials.persistence.jooq.Tables.HOLOGRAM_LINES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.vaults.JooqVaultRepository;
import com.uxplima.uxmessentials.shared.application.health.RepairStatus;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every table, index and key of the schema is written under {@code storage.table-prefix}, the way every plugin of the
 * family writes its own, so two plugins, or two copies of this one, can share a database without one table name
 * landing on another. A file that names no prefix keeps the names an install has always had.
 */
final class TablePrefixTest {

    @Test
    @DisplayName("every table and index of the schema carries the prefix, the migration history included")
    void everyNameCarriesThePrefix(@TempDir Path folder) throws SQLException {
        open(folder, Map.of("storage.table-prefix", "t_")).close();

        List<String> names = schemaNames(folder);
        assertThat(names).isNotEmpty().allSatisfy(name -> assertThat(name).startsWith("t_"));
        assertThat(names).contains("t_homes", "t_flyway_schema_history");
    }

    @Test
    @DisplayName("a file that names no prefix keeps the names an install has always had")
    void noPrefixKeepsTheNames(@TempDir Path folder) throws SQLException {
        open(folder, Map.of()).close();

        assertThat(schemaNames(folder)).contains("homes", "warps", "flyway_schema_history");
    }

    @Test
    @DisplayName("the repositories read and write the prefixed tables")
    void theRepositoriesUseThePrefix(@TempDir Path folder) {
        try (Persistence persistence = open(folder, Map.of("storage.table-prefix", "t_"))) {
            JooqVaultRepository vaults = new JooqVaultRepository(persistence.dsl());
            PlayerRef owner = new PlayerRef(UUID.randomUUID(), "Alice");
            Instant now = Instant.parse("2026-10-24T00:00:00Z");
            vaults.save(Vault.of(VaultId.of(owner, 1), new VaultSize(3), VaultContents.empty(), null, null, now));

            assertThat(vaults.find(VaultId.of(owner, 1))).isPresent();
            assertThat(persistence.probe().schemaVersion()).isPresent();
        }
    }

    @Test
    @DisplayName("the integrity check finds and repairs an orphan in the prefixed tables")
    void theIntegrityCheckReadsThePrefixedTables(@TempDir Path folder) {
        try (Persistence persistence = open(folder, Map.of("storage.table-prefix", "t_"))) {
            persistence
                    .dsl()
                    .transaction(configuration -> configuration
                            .dsl()
                            .insertInto(HOLOGRAM_LINES)
                            .set(HOLOGRAM_LINES.HOLOGRAM, "missing")
                            .set(HOLOGRAM_LINES.IDX, 0)
                            .set(HOLOGRAM_LINES.TEXT, "orphan")
                            .execute());
            var check = persistence.integrityCheck(world -> true);

            assertThat(check.check().message()).contains("orphanRows=1");
            assertThat(check.repair().status()).isEqualTo(RepairStatus.REPAIRED);
            assertThat(persistence.dsl().fetchCount(HOLOGRAM_LINES)).isZero();
        }
    }

    @Test
    @DisplayName("a prefix written over an install that ran without one stops the plugin rather than start empty")
    void aPrefixOverOldTablesStops(@TempDir Path folder) {
        open(folder, Map.of()).close();

        assertThatThrownBy(() -> open(folder, Map.of("storage.table-prefix", "uxmessentials_")))
                .isInstanceOf(PersistenceException.class)
                .hasMessageContaining("table-prefix");
    }

    @Test
    @DisplayName("an install that started under a prefix opens again under it")
    void aPrefixedInstallOpensAgain(@TempDir Path folder) {
        open(folder, Map.of("storage.table-prefix", "uxmessentials_")).close();

        open(folder, Map.of("storage.table-prefix", "uxmessentials_")).close();
    }

    @Test
    @DisplayName("a prefix is letters, digits and underscores, short enough for every index name to fit")
    void aPrefixIsABareShortWord(@TempDir Path folder) {
        assertThatThrownBy(() -> open(folder, Map.of("storage.table-prefix", "x; DROP")))
                .hasMessageContaining("table-prefix");
        assertThatThrownBy(() -> open(folder, Map.of("storage.table-prefix", "a_prefix_far_too_long_")))
                .hasMessageContaining("table-prefix");
    }

    private static Persistence open(Path folder, Map<String, String> values) {
        return Persistence.open(new Written(values), folder, List.of("db/migration"), new Silent());
    }

    /** The tables and indexes SQLite holds, without the ones it makes for itself. */
    private static List<String> schemaNames(Path folder) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("uxmessentials.db"));
                ResultSet rows = connection
                        .createStatement()
                        .executeQuery("SELECT name FROM sqlite_master WHERE type IN ('table', 'index')"
                                + " AND name NOT LIKE 'sqlite_%'")) {
            while (rows.next()) {
                names.add(rows.getString(1));
            }
        }
        return names;
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
