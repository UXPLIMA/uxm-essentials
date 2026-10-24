package com.uxplima.uxmessentials.persistence.runtime;

import java.nio.file.Path;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * A typed read view over the {@code storage} subtree of the plugin config. The factory consults this
 * rather than dotted paths so the config keys live in one place; every getter carries a default so a
 * minimal config yields the embedded-SQLite backend with no operator action.
 *
 * <p>The keys, relative to {@code storage}, are the ones every plugin of the family reads: {@code type}
 * (sqlite|mysql|mariadb|postgresql, any other word refused), the network coordinates
 * {@code host}/{@code port}/{@code database}/{@code username}/{@code password}, {@code pool-size} for a network
 * backend, {@code connection-timeout-ms}, and {@code local-only} for the one server of a network that keeps its own
 * rows. SQLite needs none of the network coordinates: its file lives under the
 * plugin data folder.
 *
 * <p>A file written before the family agreed on those names says {@code backend} and {@code read-pool-size}, and
 * nothing rewrites an operator's file, so the old name is read when the new one is not written. Without that a
 * network that kept its file would start on a SQLite file of its own and nobody would see its players' data.
 */
@NullMarked
public final class DatabaseSettings {

    private static final String SQLITE_FILE_NAME = "uxmessentials.db";

    /**
     * Letters, digits and underscores, at most fifteen of them. The longest index name of the schema is 48
     * characters, and PostgreSQL cuts a name at 63 without failing, so a longer prefix could make two names one.
     */
    private static final java.util.regex.Pattern PREFIX = java.util.regex.Pattern.compile("[A-Za-z0-9_]{0,15}");

    private final ConfigStore config;
    private final Path dataFolder;

    public DatabaseSettings(ConfigStore config, Path dataFolder) {
        this.config = Objects.requireNonNull(config, "config");
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
    }

    /**
     * The selected backend; defaults to {@link DatabaseBackend#SQLITE}. {@code storage.local-only} keeps this one
     * server on its SQLite file whatever the block names, so a network can carry the same block on every server and
     * the creative or build server sets one line.
     */
    public DatabaseBackend backend() {
        if (config.getBoolean("storage.local-only", false)) {
            return DatabaseBackend.SQLITE;
        }
        return DatabaseBackend.fromToken(
                config.getString("storage.type", config.getString("storage.backend", "sqlite")));
    }

    /**
     * Written in front of every table, index and key name, so two plugins, or two copies of this one, can share a
     * database. A file that names none keeps the bare names every install before it was written under.
     *
     * @throws IllegalArgumentException when it is not a bare word of at most fifteen characters
     */
    public String tablePrefix() {
        String prefix = config.getString("storage.table-prefix", "");
        if (!PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException("storage.table-prefix may hold only letters, digits and underscores,"
                    + " at most 15 of them, got '" + prefix + "'");
        }
        return prefix;
    }

    /** The SQLite database file under the plugin data folder. */
    public Path sqliteFile() {
        String name = config.getString("storage.file", SQLITE_FILE_NAME);
        return dataFolder.resolve(name);
    }

    /** The network host for MySQL/Postgres; default {@code localhost}. */
    public String host() {
        return config.getString("storage.host", "localhost");
    }

    /** The network port; the backend's standard port when unset or zero, the way the family writes it. */
    public int port() {
        int standard = backend() == DatabaseBackend.POSTGRES ? 5432 : 3306;
        int written = config.getInt("storage.port", 0);
        return written > 0 ? written : standard;
    }

    /** The schema/database name on a network backend; default {@code uxmessentials}. */
    public String database() {
        return config.getString("storage.database", "uxmessentials");
    }

    /** The network username; default {@code uxmessentials}. */
    public String username() {
        return config.getString("storage.username", "uxmessentials");
    }

    /** The network password; default empty. */
    public String password() {
        return config.getString("storage.password", "");
    }

    /**
     * The read-pool size for a network backend (HikariCP formula starts at 8 to 10 for a small host);
     * default 8. Ignored for SQLite, whose write pool is fixed at one connection.
     */
    public int readPoolSize() {
        return Math.max(1, config.getInt("storage.pool-size", config.getInt("storage.read-pool-size", 8)));
    }

    /** The connection-acquisition timeout in milliseconds; default 5000. */
    public int connectionTimeoutMillis() {
        return Math.max(1000, config.getInt("storage.connection-timeout-ms", 5000));
    }
}
