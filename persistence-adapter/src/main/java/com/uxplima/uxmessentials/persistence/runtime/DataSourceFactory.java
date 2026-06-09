package com.uxplima.uxmessentials.persistence.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the HikariCP {@link DataSource} for the configured backend.
 *
 * <p>The embedded-SQLite default is single-writer: the pool is sized to exactly one connection, WAL
 * mode is enabled so readers proceed while a writer is active, {@code synchronous=NORMAL} is safe under
 * WAL, and {@code busy_timeout} makes a contended writer wait rather than fail ({@code
 * docs/02-concurrency.md} §7.1). The network backends (MySQL/MariaDB, PostgreSQL) have real row-level
 * locking, so they use a conventional multi-connection pool sized from config (§7.2). The factory
 * builds the data source only; migration and the {@code DSLContext} are layered on top by their own
 * components.
 */
@NullMarked
public final class DataSourceFactory {

    private DataSourceFactory() {}

    /** Build the backing {@link HikariDataSource} for {@code settings}' selected backend. */
    public static HikariDataSource create(DatabaseSettings settings) {
        Objects.requireNonNull(settings, "settings");
        DatabaseBackend backend = settings.backend();
        HikariConfig config = backend.isSingleWriter() ? sqlite(settings) : network(settings, backend);
        config.setPoolName("uxmessentials-" + backend.jdbcSubprotocol());
        config.setConnectionTimeout(settings.connectionTimeoutMillis());
        config.setAutoCommit(false);
        return new HikariDataSource(config);
    }

    private static HikariConfig sqlite(DatabaseSettings settings) {
        Path file = settings.sqliteFile();
        ensureParentDirectory(file);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + file.toAbsolutePath());
        config.setDriverClassName(DatabaseBackend.SQLITE.driverClassName());
        // The single dedicated write connection is the single-writer invariant; readers proceed
        // concurrently against the WAL on this same connection without contending for the write lock.
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("busy_timeout", "5000");
        config.addDataSourceProperty("foreign_keys", "ON");
        return config;
    }

    private static HikariConfig network(DatabaseSettings settings, DatabaseBackend backend) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(networkUrl(settings, backend));
        config.setDriverClassName(backend.driverClassName());
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        int pool = settings.readPoolSize();
        config.setMaximumPoolSize(pool);
        config.setMinimumIdle(pool);
        config.setValidationTimeout(1_000);
        config.setLeakDetectionThreshold(10_000);
        return config;
    }

    private static String networkUrl(DatabaseSettings settings, DatabaseBackend backend) {
        return "jdbc:%s://%s:%d/%s"
                .formatted(backend.jdbcSubprotocol(), settings.host(), settings.port(), settings.database());
    }

    private static void ensureParentDirectory(Path file) {
        Path parent = file.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException cause) {
            throw new PersistenceException("could not create database directory " + parent, cause);
        }
    }
}
