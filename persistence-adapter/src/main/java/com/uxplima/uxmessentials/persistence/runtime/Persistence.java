package com.uxplima.uxmessentials.persistence.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.health.RepairableHealthCheck;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Dialect;
import com.uxplima.uxmlib.storage.sql.StorageCopy;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;

/**
 * The persistence layer's lifecycle handle, opened once on plugin enable and closed on disable.
 *
 * <p>Opening it resolves the backend from config, builds the HikariCP data source (single-writer WAL for
 * the SQLite default, a pooled connection set for the network backends), runs the Flyway migrations
 * forward, and exposes the shared jOOQ {@link DSLContext} every repository binds to. Closing it shuts the
 * pool down. The contexts that own tables construct their repositories over {@link #dsl()}; this facade
 * does not know about specific tables. It is the single object bootstrap threads through the module
 * wiring so a context that declares migrations and repositories has a {@code DSLContext}.
 */
@NullMarked
public final class Persistence implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final DSLContext dsl;
    private final DatabaseBackend backend;
    private final String tablePrefix;

    private Persistence(HikariDataSource dataSource, DSLContext dsl, DatabaseBackend backend, String tablePrefix) {
        this.dataSource = dataSource;
        this.dsl = dsl;
        this.backend = backend;
        this.tablePrefix = tablePrefix;
    }

    /**
     * Open the persistence layer: build the data source, run migrations under {@code migrationLocations}
     * (or the V1 baseline at {@code db/migration} when empty), and create the shared context.
     *
     * @param config the (whole) plugin config; the {@code storage} subtree selects the backend
     * @param dataFolder the plugin data folder the embedded SQLite file lives under
     * @param migrationLocations the classpath Flyway locations the enabled modules contribute
     * @param log operator-facing diagnostics
     */
    public static Persistence open(ConfigStore config, Path dataFolder, List<String> migrationLocations, Logger log) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(migrationLocations, "migrationLocations");
        Objects.requireNonNull(log, "log");
        DatabaseSettings settings = new DatabaseSettings(config, dataFolder);
        DatabaseBackend backend = settings.backend();
        settings.unknownType()
                .ifPresent(word -> log.warn(
                        "storage.type '{}' names no database. This server has run on its SQLite file under that word,"
                                + " so it keeps doing so: write sqlite to say it, or mysql, mariadb or postgresql.",
                        word));
        String tablePrefix = settings.tablePrefix();
        HikariDataSource dataSource = DataSourceFactory.create(settings);
        try {
            if (backend != DatabaseBackend.SQLITE && settings.copyFromFile()) {
                migrateFillingFromFile(config, dataFolder, dataSource, backend, tablePrefix, migrationLocations, log);
            } else {
                new FlywayMigrationRunner(dataSource, backend, tablePrefix).migrate(migrationLocations);
            }
        } catch (RuntimeException cause) {
            dataSource.close();
            throw cause;
        }
        log.info("persistence opened on {} backend", backend.jdbcSubprotocol());
        return new Persistence(
                dataSource,
                DslContextProvider.create(dataSource, backend.dialect(), tablePrefix),
                backend,
                tablePrefix);
    }

    /**
     * Migrate the network database, and fill it from the SQLite file the block names when this start is the one that
     * built the schema there. None of the file's tables in the database before the migration is what says so: the
     * tables then hold only the rows the migrations seed, and the file's own rows replace them in one transaction. A
     * database that had the schema before is left alone, so the key left on copies nothing twice.
     */
    private static void migrateFillingFromFile(
            ConfigStore config,
            Path dataFolder,
            HikariDataSource target,
            DatabaseBackend backend,
            String tablePrefix,
            List<String> migrationLocations,
            Logger log) {
        FlywayMigrationRunner targetMigrations = new FlywayMigrationRunner(target, backend, tablePrefix);
        DatabaseSettings local = new DatabaseSettings(new LocalOnly(config), dataFolder);
        Path file = local.sqliteFile();
        // Opening a SQLite pool creates the file, and an empty one left behind would read as rows that vanished.
        if (!Files.isRegularFile(file)) {
            targetMigrations.migrate(migrationLocations);
            log.warn("storage.copy-from-file found no {} to copy from", file.getFileName());
            return;
        }
        Database into = Database.adopt(target, Dialect.fromJdbcUrl("jdbc:" + backend.jdbcSubprotocol() + ":"));
        try (HikariDataSource source = DataSourceFactory.create(local)) {
            new FlywayMigrationRunner(source, DatabaseBackend.SQLITE, tablePrefix).migrate(migrationLocations);
            Database from = Database.adopt(source, Dialect.SQLITE);
            boolean fresh = Collections.disjoint(
                    StorageCopy.tablesUnder(from, tablePrefix), StorageCopy.tablesUnder(into, tablePrefix));
            targetMigrations.migrate(migrationLocations);
            if (!fresh) {
                log.info("storage.copy-from-file copied nothing: the tables were in the database before this start."
                        + " Set it to false.");
                return;
            }
            StorageCopy.Copied copied = StorageCopy.refill(from, into, tablePrefix);
            log.info(
                    "storage.copy-from-file copied {} rows from {}. Set it to false.",
                    copied.total(),
                    file.getFileName());
        } catch (StorageCopy.Refused refused) {
            log.warn(
                    "storage.copy-from-file copied nothing ({} on {}); see the storage page",
                    refused.reason(),
                    refused.table());
        }
    }

    /** The shared jOOQ context every repository binds to. */
    public DSLContext dsl() {
        return dsl;
    }

    /** The active backend, for diagnostics and backend-specific repository decisions. */
    public DatabaseBackend backend() {
        return backend;
    }

    /** Build the cross-context integrity check without leaking jOOQ into the bootstrap adapter. */
    public RepairableHealthCheck integrityCheck(java.util.function.Predicate<String> worldExists) {
        return new PersistenceIntegrityHealthCheck(dsl, worldExists, tablePrefix);
    }

    /**
     * A lightweight liveness probe for the {@code /uxmess doctor} diagnostic: runs a trivial {@code SELECT 1}
     * to confirm a connection can be acquired and read the latest applied Flyway schema version from the
     * history table. Returns a {@link Probe} carrying both rather than throwing. A dead pool surfaces as a
     * {@code reachable = false} probe so the caller reports it instead of propagating. Must be called off the
     * tick thread (it acquires a connection).
     */
    public Probe probe() {
        try {
            Integer one = dsl.selectOne().fetchOne(0, Integer.class);
            boolean reachable = one != null && one == 1;
            return new Probe(reachable, backend.jdbcSubprotocol(), appliedVersion());
        } catch (RuntimeException unreachable) {
            return new Probe(false, backend.jdbcSubprotocol(), Optional.empty());
        }
    }

    private Optional<String> appliedVersion() {
        try {
            // The bare name: the context writes the table prefix in front of every table it renders. The order
            // Flyway applied them in, not the largest version: the column is text, and as text "9"
            // sorts after "85", so a database on V85 reported itself as v9.
            Record1<Object> row = dsl.select(DSL.field("version"))
                    .from(DSL.table(DSL.name(FlywayMigrationRunner.historyTable(""))))
                    .where(DSL.field("success").eq(true))
                    .and(DSL.field("version").isNotNull())
                    .orderBy(DSL.field("installed_rank").desc())
                    .limit(1)
                    .fetchOne();
            Object version = row == null ? null : row.value1();
            return Optional.ofNullable(version).map(Object::toString);
        } catch (RuntimeException noHistoryTable) {
            // A backend without the Flyway history table (or an in-flight first migration) has no version to
            // report; the liveness half of the probe still stands on its own.
            return Optional.empty();
        }
    }

    /**
     * The result of a {@link #probe()}: whether a connection answered {@code SELECT 1}, the backend label, and
     * the latest applied Flyway version when the history table is readable.
     *
     * @param reachable whether the trivial query succeeded
     * @param backend the backend's JDBC subprotocol label, for the diagnostic line
     * @param schemaVersion the latest applied Flyway version, or empty when none is readable
     */
    public record Probe(boolean reachable, String backend, Optional<String> schemaVersion) {
        public Probe {
            Objects.requireNonNull(backend, "backend");
            Objects.requireNonNull(schemaVersion, "schemaVersion");
        }
    }

    /** Shut the connection pool down. Called on plugin disable, after the modules have stopped. */
    @Override
    public void close() {
        dataSource.close();
    }

    /** The block as a server that keeps its rows on the SQLite file reads it. */
    private record LocalOnly(ConfigStore config) implements ConfigStore {

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return path.equals("storage.local-only") || config.getBoolean(path, fallback);
        }

        @Override
        public String getString(String path, String fallback) {
            return config.getString(path, fallback);
        }

        @Override
        public int getInt(String path, int fallback) {
            return config.getInt(path, fallback);
        }
    }
}
