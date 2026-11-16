package com.uxplima.uxmessentials.persistence.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.persistence.jooq.DefaultSchema;
import com.uxplima.uxmessentials.persistence.runtime.DataSourceFactory;
import com.uxplima.uxmessentials.persistence.runtime.DatabaseSettings;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmlib.storage.sql.StorageSettings;
import com.uxplima.uxmlib.storage.testing.RealDatabase;
import com.uxplima.uxmlib.storage.testing.RealDatabases;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.sqlite.SQLiteDataSource;

/**
 * Hands each test of an {@link EveryBackend} class a migrated, empty {@link TestDatabase} on the backend this JVM
 * runs.
 *
 * <p>Running every migration for every test is what made the store tests slow, so the migrations run once per JVM:
 * on SQLite into a template file each test gets a copy of, and on a server into one database whose rows are cleared
 * before each test. The rows a migration writes itself (the lockdown switch, the announcer settings) are read once
 * after migrating and put back after every clear, so each test starts from what a fresh install holds.
 *
 * <p>On a server the database is opened the way the plugin opens it: {@link DataSourceFactory} builds the pool from
 * the same {@code storage} keys, with auto-commit off, so a write that forgets its transaction is lost here as it
 * would be on an operator's server. Without Docker the tests are skipped with the reason, unless
 * {@code uxm.requireDocker} is set, as CI does, where they fail instead.
 */
final class Backends implements ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(Backends.class);

    private static final Map<TestBackend, Server> SERVERS = new EnumMap<>(TestBackend.class);

    private static final Map<TestBackend, RuntimeException> UNAVAILABLE = new EnumMap<>(TestBackend.class);

    private static Path sqliteTemplate;

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == TestDatabase.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context)
            throws ParameterResolutionException {
        ExtensionContext test = context.getTestMethod().isPresent() ? context : context.getRoot();
        return test.getStore(NAMESPACE)
                .getOrComputeIfAbsent("database", key -> open(TestBackend.selected()), Opened.class)
                .database();
    }

    private static Opened open(TestBackend backend) {
        return backend.isFile() ? sqlite() : server(backend).fresh();
    }

    private static Opened sqlite() {
        try {
            Path folder = Files.createTempDirectory("uxm-store-test");
            Path file = folder.resolve("data.db");
            Files.copy(template(), file);
            SQLiteDataSource data = new SQLiteDataSource();
            data.setUrl("jdbc:sqlite:" + file);
            data.setJournalMode("WAL");
            data.setBusyTimeout(5000);
            DSLContext dsl =
                    DslContextProvider.create(data, TestBackend.SQLITE.runtime().dialect(), "");
            return new Opened(new TestDatabase(TestBackend.SQLITE, data, dsl), () -> delete(folder));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** The migrated SQLite file every test copies, migrated the first time a test asks for one. */
    private static synchronized Path template() throws IOException {
        if (sqliteTemplate == null) {
            Path folder = Files.createTempDirectory("uxm-store-template");
            Path file = folder.resolve("template.db");
            SQLiteDataSource data = new SQLiteDataSource();
            data.setUrl("jdbc:sqlite:" + file);
            new FlywayMigrationRunner(data, TestBackend.SQLITE.runtime(), "").migrate(List.of());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> delete(folder)));
            sqliteTemplate = file;
        }
        return sqliteTemplate;
    }

    private static synchronized Server server(TestBackend backend) {
        RuntimeException failed = UNAVAILABLE.get(backend);
        if (failed == null && !SERVERS.containsKey(backend)) {
            try {
                SERVERS.put(backend, Server.start(backend));
            } catch (RuntimeException failure) {
                UNAVAILABLE.put(backend, failure);
                failed = failure;
            }
        }
        if (failed != null) {
            if (Boolean.getBoolean("uxm.requireDocker")) {
                throw new IllegalStateException(backend.id() + " could not be started", failed);
            }
            Assumptions.abort(backend.id() + " needs Docker, which is not available here: " + failed.getMessage());
        }
        return SERVERS.get(backend);
    }

    private static void delete(Path folder) {
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // A temporary folder the operating system clears anyway; a file still held open must not fail a test.
        }
    }

    /** A database a test holds, and what to do once the test is over. */
    private record Opened(TestDatabase database, Runnable cleanup) implements ExtensionContext.Store.CloseableResource {

        @Override
        public void close() {
            cleanup.run();
        }
    }

    /** One migrated database on a server container, shared by every test of the JVM and cleared between them. */
    private static final class Server {

        private final TestBackend backend;
        private final HikariDataSource pool;
        private final DSLContext dsl;
        private final Map<Table<?>, Result<Record>> seeds;

        private Server(
                TestBackend backend, HikariDataSource pool, DSLContext dsl, Map<Table<?>, Result<Record>> seeds) {
            this.backend = backend;
            this.pool = pool;
            this.dsl = dsl;
            this.seeds = seeds;
        }

        static Server start(TestBackend backend) {
            RealDatabase real = RealDatabases.fresh(backend.server());
            StorageSettings coordinates = real.settings();
            Map<String, Object> storage = Map.of(
                    "storage.type", backend.id(),
                    "storage.host", coordinates.host(),
                    "storage.port", coordinates.port(),
                    "storage.database", coordinates.database(),
                    "storage.username", coordinates.username(),
                    "storage.password", coordinates.password(),
                    "storage.pool-size", 4);
            HikariDataSource pool =
                    DataSourceFactory.create(new DatabaseSettings(new MapConfigStore(storage), real.dataFolder()));
            new FlywayMigrationRunner(pool, backend.runtime(), "").migrate(List.of());
            DSLContext dsl = DslContextProvider.create(pool, backend.runtime().dialect(), "");
            Map<Table<?>, Result<Record>> seeds = new LinkedHashMap<>();
            for (Table<?> table : tables()) {
                Result<Record> rows = dsl.select().from(table).fetch();
                if (rows.isNotEmpty()) {
                    seeds.put(table, rows);
                }
            }
            Runtime.getRuntime().addShutdownHook(new Thread(pool::close));
            return new Server(backend, pool, dsl, seeds);
        }

        Opened fresh() {
            clear();
            return new Opened(new TestDatabase(backend, pool, dsl), () -> {});
        }

        /** Every table emptied and the migrations' own rows written back, in one transaction. */
        private void clear() {
            dsl.transaction(configuration -> {
                DSLContext local = configuration.dsl();
                List<Table<?>> tables = tables();
                if (local.dialect().family() == SQLDialect.POSTGRES) {
                    // One statement for every table: PostgreSQL clears them together, references included.
                    local.execute("TRUNCATE TABLE "
                            + String.join(
                                    ", ", tables.stream().map(local::render).toList()) + " CASCADE");
                } else {
                    // A delete per table, in one round trip, with references unchecked so their order does not matter.
                    local.execute("SET FOREIGN_KEY_CHECKS = 0");
                    local.batch(tables.stream()
                                    .map(table -> (Query) local.deleteFrom(table))
                                    .toList())
                            .execute();
                    local.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
                seeds.forEach((table, rows) -> rows.forEach(
                        row -> local.insertInto(table).set(row.intoMap()).execute()));
            });
        }
    }

    /** Every table the migrations create, as the generated schema knows them. */
    private static List<Table<?>> tables() {
        return DefaultSchema.DEFAULT_SCHEMA.getTables();
    }
}
