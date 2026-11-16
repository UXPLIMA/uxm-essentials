package com.uxplima.uxmessentials.persistence.testing;

import java.util.Arrays;
import java.util.Locale;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmlib.storage.sql.Backend;

/**
 * The database a repository test runs on. The build picks one per test JVM through {@code uxm.backend}: SQLite in
 * every {@code test} run, and one network server in each of the real-database tasks.
 */
public enum TestBackend {
    SQLITE("sqlite", DatabaseBackend.SQLITE, Backend.SQLITE),
    MYSQL("mysql", DatabaseBackend.MARIADB, Backend.MYSQL),
    MARIADB("mariadb", DatabaseBackend.MARIADB, Backend.MARIADB),
    POSTGRESQL("postgresql", DatabaseBackend.POSTGRES, Backend.POSTGRESQL);

    /** The system property the build sets on a test JVM to name its backend. */
    public static final String PROPERTY = "uxm.backend";

    private final String id;
    private final DatabaseBackend runtime;
    private final Backend server;

    TestBackend(String id, DatabaseBackend runtime, Backend server) {
        this.id = id;
        this.runtime = runtime;
        this.server = server;
    }

    /** The backend this JVM runs its repository tests on; SQLite when the build names none. */
    public static TestBackend selected() {
        String wanted = System.getProperty(PROPERTY, "sqlite").trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(backend -> backend.id.equals(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("uxm.backend names no backend: " + wanted));
    }

    /** The word {@code storage.type} takes for this backend in {@code config.conf}. */
    public String id() {
        return id;
    }

    /** The backend the plugin's persistence layer runs this database as. */
    public DatabaseBackend runtime() {
        return runtime;
    }

    /** The server the shared test containers start for this backend. */
    Backend server() {
        return server;
    }

    /** Whether this backend is a file, which needs no server. */
    public boolean isFile() {
        return this == SQLITE;
    }
}
