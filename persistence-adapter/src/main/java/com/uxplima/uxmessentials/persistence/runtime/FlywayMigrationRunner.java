package com.uxplima.uxmessentials.persistence.runtime;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.logging.LogFactory;
import org.jspecify.annotations.NullMarked;

/**
 * Applies the Flyway migrations on plugin enable, forward and idempotently.
 *
 * <p>The runner is given the migration classpath locations the enabled modules own (the V1 baseline at
 * {@code db/migration}, plus each context's own location as it lands) and the live {@link DataSource}.
 * It repairs then migrates: an already-migrated database is a no-op, a fresh one gets the baseline. The
 * repair realigns the schema-history checksums with the resolved scripts before applying anything, so a
 * cosmetic edit to an already-applied script (e.g. a reworded comment) self-heals on the next start
 * instead of failing validation and blocking enable on a server that ran the old script. The same scripts
 * run on every backend. The portable DDL means SQLite, MySQL and PostgreSQL converge on the same schema
 * (the backend-parity invariant). A migration failure is fatal and surfaced, never swallowed.
 */
@NullMarked
public final class FlywayMigrationRunner {

    private final DataSource dataSource;
    private final DatabaseBackend backend;
    private final String tablePrefix;

    /** A table the first migration creates, so its bare name marks an install that ran without a prefix. */
    private static final String BARE_MARKER_TABLE = "homes";

    /** @param tablePrefix written into every name the migrations create, and in front of the history table */
    public FlywayMigrationRunner(DataSource dataSource, DatabaseBackend backend, String tablePrefix) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.tablePrefix = Objects.requireNonNull(tablePrefix, "tablePrefix");
    }

    /** The table Flyway records what it ran in. A file without a prefix keeps Flyway's own name. */
    public static String historyTable(String tablePrefix) {
        return tablePrefix + "flyway_schema_history";
    }

    /**
     * What each migration connection runs first. On MySQL and MariaDB it adds {@code ANSI_QUOTES}, so a column named
     * after a reserved word can be written in double quotes, the one quoting SQLite and PostgreSQL also read as a
     * name. V65 named a column {@code lines} unquoted, and the plugin stopped there on both servers. Only Flyway's own
     * connections run this: the queries at runtime keep the server's mode.
     */
    public static String initSql(DatabaseBackend backend) {
        return backend == DatabaseBackend.MARIADB
                ? "SET SESSION sql_mode = CONCAT_WS(',', NULLIF(@@SESSION.sql_mode, ''), 'ANSI_QUOTES')"
                : "";
    }

    /**
     * Run every migration found under {@code locations}, creating the schema on a fresh database and
     * doing nothing on an already-current one. Locations are classpath-relative Flyway directories.
     */
    public void migrate(List<String> locations) {
        Objects.requireNonNull(locations, "locations");
        refuseAPrefixOverBareTables();
        String[] resolved = locations.isEmpty() ? new String[] {"classpath:db/migration"} : prefix(locations);
        FluentConfiguration configuration = Flyway.configure(getClass().getClassLoader())
                .dataSource(dataSource)
                .locations(resolved)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .initSql(initSql(backend))
                // Every name in the scripts is written as ${prefix}name. The repair below realigns the recorded
                // checksums of an install that ran them before the placeholder was written in.
                .placeholders(Map.of("prefix", tablePrefix))
                .table(historyTable(tablePrefix));
        try {
            Flyway flyway = configuration.load();
            // Flyway's log, without the warning MariaDB writes for every named primary key: see QuietFlywayLog. Set as
            // an instance, because Flyway looks a class name up in its own loader, which cannot see this plugin's
            // classes, and set after load, because building a Flyway resets it. Flyway is loaded for this plugin
            // alone, so the setting reaches no other plugin.
            LogFactory.setLogCreator(new QuietFlywayLog());
            // Realign recorded checksums with the resolved scripts before validating, so a cosmetic edit to
            // an already-applied migration (e.g. a reworded comment) does not brick a server that ran the
            // old script. Then apply forward; an up-to-date database is a no-op.
            flyway.repair();
            flyway.migrate();
        } catch (FlywayException cause) {
            throw new PersistenceException("database migration failed for locations " + locations, cause);
        }
    }

    /**
     * Stop when a prefix is written over a database that already holds this plugin's tables under their bare names.
     * Those are an install that ran before the prefix was chosen; migrating would build an empty schema beside them,
     * and every home, balance and warp would seem gone.
     */
    private void refuseAPrefixOverBareTables() {
        if (tablePrefix.isEmpty()) {
            return;
        }
        Set<String> tables = tableNames();
        if (!tables.contains(historyTable(tablePrefix).toLowerCase(Locale.ROOT))
                && tables.contains(historyTable(""))
                && tables.contains(BARE_MARKER_TABLE)) {
            throw new PersistenceException("storage.table-prefix is '" + tablePrefix + "', and this database already"
                    + " holds uxmEssentials tables without a prefix. Write table-prefix = \"\" to keep using them.");
        }
    }

    private Set<String> tableNames() {
        Set<String> names = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
                ResultSet rows = connection
                        .getMetaData()
                        .getTables(connection.getCatalog(), connection.getSchema(), "%", new String[] {"TABLE"})) {
            while (rows.next()) {
                names.add(rows.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
        } catch (SQLException unreadable) {
            throw new PersistenceException("could not list the tables of the database", unreadable);
        }
        return names;
    }

    private static String[] prefix(List<String> locations) {
        return locations.stream()
                .map(location -> location.startsWith("classpath:") ? location : "classpath:" + location)
                .toArray(String[]::new);
    }
}
