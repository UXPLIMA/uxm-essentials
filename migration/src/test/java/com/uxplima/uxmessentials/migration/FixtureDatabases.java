package com.uxplima.uxmessentials.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Builds the database files a JDBC source reads from the SQL scripts under {@code src/test/resources}, in the place
 * the source looks for them on a real server, so the import opens a real file the way it opens the foreign plugin's.
 */
public final class FixtureDatabases {

    private FixtureDatabases() {}

    /**
     * An H2 file at {@code mvDbFile} (which must end in {@code .mv.db}) holding what {@code script} creates.
     *
     * @return {@code mvDbFile}
     */
    public static Path h2(String script, Path mvDbFile) {
        String full = mvDbFile.toAbsolutePath().toString();
        String base = full.substring(0, full.length() - ".mv.db".length());
        createParent(mvDbFile);
        run("jdbc:h2:file:" + base, script);
        return mvDbFile;
    }

    /**
     * A SQLite file at {@code dbFile} holding what {@code script} creates.
     *
     * @return {@code dbFile}
     */
    public static Path sqlite(String script, Path dbFile) {
        createParent(dbFile);
        run("jdbc:sqlite:" + dbFile.toAbsolutePath(), script);
        return dbFile;
    }

    private static void run(String url, String script) {
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            for (String sql : statements(script)) {
                statement.execute(sql);
            }
        } catch (SQLException failed) {
            throw new IllegalStateException("could not build the fixture database from " + script, failed);
        }
    }

    /** The script's statements: line comments dropped, split on the semicolon that ends each one. */
    private static String[] statements(String script) {
        try {
            StringBuilder body = new StringBuilder();
            for (String line : Files.readAllLines(Fixtures.path(script))) {
                if (!line.strip().startsWith("--")) {
                    body.append(line).append('\n');
                }
            }
            return java.util.Arrays.stream(body.toString().split(";"))
                    .map(String::strip)
                    .filter(sql -> !sql.isEmpty())
                    .toArray(String[]::new);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static void createParent(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
