package com.uxplima.uxmessentials.persistence.runtime;

import static com.uxplima.uxmessentials.persistence.jooq.Tables.HOLOGRAM_LINES;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.StorageSettings;
import com.uxplima.uxmlib.storage.testing.EveryBackend;
import com.uxplima.uxmlib.storage.testing.RealDatabase;

/**
 * The whole schema, every migration from the first, on SQLite, MySQL, MariaDB and PostgreSQL, under a prefix and
 * without one. Until this ran only SQLite was proved in the build, and the network backends were replayed by hand.
 */
final class SchemaOnEveryServerTest {

    @EveryBackend
    void theSchemaIsBuiltUnderThePrefix(RealDatabase database) throws SQLException {
        try (database;
                Persistence persistence = open(database, "t_")) {
            List<String> names = tableNames(database);

            assertThat(names).contains("t_homes", "t_flyway_schema_history");
            assertThat(names).allSatisfy(name -> assertThat(name).startsWith("t_"));
            assertThat(persistence.probe().schemaVersion()).isPresent();
        }
    }

    @EveryBackend
    void theRepositoriesAndTheIntegrityCheckUseThePrefix(RealDatabase database) {
        try (database;
                Persistence persistence = open(database, "t_")) {
            JooqVaultRepository vaults = new JooqVaultRepository(persistence.dsl());
            PlayerRef owner = new PlayerRef(UUID.randomUUID(), "Alice");
            vaults.save(
                    Vault.of(VaultId.of(owner, 1), new VaultSize(3), VaultContents.empty(), null, null, Instant.EPOCH));
            assertThat(vaults.find(VaultId.of(owner, 1))).isPresent();

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
        }
    }

    @EveryBackend
    void anInstallWithoutAPrefixKeepsTheBareNamesAndOpensAgain(RealDatabase database) throws SQLException {
        try (database) {
            open(database, "").close();
            open(database, "").close();

            assertThat(tableNames(database)).contains("homes", "flyway_schema_history");
        }
    }

    private static Persistence open(RealDatabase database, String prefix) {
        StorageSettings storage = database.settings();
        Map<String, String> values = new HashMap<>();
        values.put("storage.type", storage.backend().id());
        values.put("storage.host", storage.host());
        values.put("storage.port", String.valueOf(storage.port()));
        values.put("storage.database", storage.database());
        values.put("storage.username", storage.username());
        values.put("storage.password", storage.password());
        values.put("storage.table-prefix", prefix);
        return Persistence.open(new Written(values), database.dataFolder(), List.of("db/migration"), new Silent());
    }

    /** The tables of the database, lower case. The SQLite file is the one this plugin names in the folder. */
    private static List<String> tableNames(RealDatabase database) throws SQLException {
        if (database.backend().isFile()) {
            try (Connection connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + database.dataFolder().resolve("uxmessentials.db"))) {
                return tableNames(connection);
            }
        }
        try (Database pool = database.open();
                Connection connection = pool.dataSource().getConnection()) {
            return tableNames(connection);
        }
    }

    private static List<String> tableNames(Connection connection) throws SQLException {
        List<String> names = new ArrayList<>();
        try (ResultSet rows = connection
                .getMetaData()
                .getTables(connection.getCatalog(), connection.getSchema(), "%", new String[] {"TABLE"})) {
            while (rows.next()) {
                names.add(rows.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
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
