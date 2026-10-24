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
 * backend, and {@code connection-timeout-ms}. SQLite needs none of the network coordinates: its file lives under the
 * plugin data folder.
 *
 * <p>A file written before the family agreed on those names says {@code backend} and {@code read-pool-size}, and
 * nothing rewrites an operator's file, so the old name is read when the new one is not written. Without that a
 * network that kept its file would start on a SQLite file of its own and nobody would see its players' data.
 */
@NullMarked
public final class DatabaseSettings {

    private static final String SQLITE_FILE_NAME = "uxmessentials.db";

    private final ConfigStore config;
    private final Path dataFolder;

    public DatabaseSettings(ConfigStore config, Path dataFolder) {
        this.config = Objects.requireNonNull(config, "config");
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
    }

    /** The selected backend; defaults to {@link DatabaseBackend#SQLITE}. */
    public DatabaseBackend backend() {
        return DatabaseBackend.fromToken(
                config.getString("storage.type", config.getString("storage.backend", "sqlite")));
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
