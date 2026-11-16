package com.uxplima.uxmessentials.persistence.runtime;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;

import javax.sql.DataSource;

import org.jooq.SQLDialect;
import org.jspecify.annotations.NullMarked;

/**
 * The relational backend the plugin runs against. SQLite is the default: single-node servers ship with it and never
 * touch a network database. MySQL/MariaDB and PostgreSQL are the network options an operator selects with {@code
 * storage.type} in {@code config.conf}; the same migrations and the same generated jOOQ DSL run against every backend
 * (the backend-parity invariant).
 *
 * <p>Each constant carries its jOOQ {@link SQLDialect} ({@link #dialectOn} tells MySQL from MariaDB on the live
 * connection), and the JDBC sub-protocol the {@code DataSourceFactory} builds the
 * URL from. SQLite additionally marks itself single-writer so the factory sizes the pool to one
 * connection and enables WAL.
 */
@NullMarked
public enum DatabaseBackend {

    /** Embedded file database; the default. Single-writer: WAL, write pool sized 1. */
    SQLITE("sqlite", "org.sqlite.JDBC", SQLDialect.SQLITE, true),

    /**
     * MySQL/MariaDB over the network. The MariaDB driver speaks both wire protocols, but the two servers do not
     * accept the same SQL, so the dialect is the server's own, read from the connection by {@link #dialectOn}.
     */
    MARIADB("mariadb", "org.mariadb.jdbc.Driver", SQLDialect.MYSQL, false),

    /** PostgreSQL over the network. */
    POSTGRES("postgresql", "org.postgresql.Driver", SQLDialect.POSTGRES, false);

    private final String jdbcSubprotocol;
    private final String driverClassName;
    private final SQLDialect dialect;
    private final boolean singleWriter;

    DatabaseBackend(String jdbcSubprotocol, String driverClassName, SQLDialect dialect, boolean singleWriter) {
        this.jdbcSubprotocol = jdbcSubprotocol;
        this.driverClassName = driverClassName;
        this.dialect = dialect;
        this.singleWriter = singleWriter;
    }

    /** The JDBC sub-protocol, e.g. {@code sqlite} in {@code jdbc:sqlite:...}. */
    public String jdbcSubprotocol() {
        return jdbcSubprotocol;
    }

    /**
     * The JDBC driver class to register explicitly. The drivers are supplied by the Paper library
     * loader into the plugin's isolated classloader, which {@code DriverManager} (loaded by the system
     * classloader) does not scan; naming the class so HikariCP loads it directly is what makes a
     * network backend connect instead of failing with "No suitable driver".
     */
    public String driverClassName() {
        return driverClassName;
    }

    /**
     * The jOOQ dialect this backend renders SQL in when the server behind it is not asked. For MySQL/MariaDB that is
     * MySQL's; a {@code DSLContext} over a live server takes {@link #dialectOn} instead.
     */
    public SQLDialect dialect() {
        return dialect;
    }

    /**
     * The jOOQ dialect for the server behind {@code dataSource}. MySQL and MariaDB share this backend but not all of
     * their SQL: an upsert in MySQL's dialect names the inserted row with an alias MariaDB has no syntax for, and a
     * row limit in MariaDB's is a clause MySQL rejects. The server names itself in its version string, so one
     * connection settles which it is.
     *
     * @throws PersistenceException when no connection can be opened to ask
     */
    public SQLDialect dialectOn(DataSource dataSource) {
        if (this != MARIADB) {
            return dialect;
        }
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData server = connection.getMetaData();
            String product = server.getDatabaseProductName() + " " + server.getDatabaseProductVersion();
            return product.toLowerCase(Locale.ROOT).contains("mariadb") ? SQLDialect.MARIADB : SQLDialect.MYSQL;
        } catch (SQLException failure) {
            throw new PersistenceException("could not ask the database whether it is MySQL or MariaDB", failure);
        }
    }

    /** True when the engine permits only one concurrent writer (SQLite). */
    public boolean isSingleWriter() {
        return singleWriter;
    }

    /**
     * Resolve a backend from a config token. Recognises {@code sqlite}, {@code mysql}/{@code mariadb},
     * and {@code postgres}/{@code postgresql}. Anything else stops the plugin. A typo that fell back to
     * SQLite would start a network server on an empty file of its own, apart from the rest of the network,
     * with only a log line to say so.
     *
     * @throws IllegalArgumentException for a word that names no backend
     */
    public static DatabaseBackend fromToken(String token) {
        return switch (token.trim().toLowerCase(Locale.ROOT)) {
            case "sqlite" -> SQLITE;
            case "mysql", "mariadb" -> MARIADB;
            case "postgres", "postgresql", "psql" -> POSTGRES;
            default ->
                throw new IllegalArgumentException(
                        "storage.type names no database: '" + token + "'. Write sqlite, mysql, mariadb or postgresql.");
        };
    }
}
