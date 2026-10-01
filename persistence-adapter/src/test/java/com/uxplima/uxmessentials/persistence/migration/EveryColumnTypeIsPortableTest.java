package com.uxplima.uxmessentials.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every column type in a migration is one SQLite, MySQL, MariaDB and PostgreSQL all accept.
 *
 * <p>V18 declared four columns {@code DOUBLE} where every other script says {@code DOUBLE PRECISION}. SQLite and MySQL
 * take either, PostgreSQL knows only the second, so the plugin stopped at V18 on PostgreSQL and never started there.
 * The types below are the ones every script was replayed against all three servers with.
 */
class EveryColumnTypeIsPortableTest {

    private static final Set<String> PORTABLE =
            Set.of("VARCHAR", "BIGINT", "DOUBLE PRECISION", "INT", "INTEGER", "REAL", "TEXT", "DECIMAL", "SMALLINT");

    /** A column definition or an added column: a name, then its type. */
    private static final Pattern COLUMN = Pattern.compile(
            "^\\s*(?:ALTER TABLE \\w+ ADD COLUMN )?\"?[a-z_][a-z0-9_]*\"?\\s+([A-Z]+(?: PRECISION)?)\\b");

    private static final Set<String> NOT_A_TYPE = Set.of("PRIMARY", "UNIQUE", "FOREIGN", "CHECK", "REFERENCES");

    @Test
    @DisplayName("every column type is one all three servers accept")
    void everyTypeIsPortable() throws IOException {
        List<String> foreign = new ArrayList<>();
        int columns = 0;
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path script : files.filter(path -> path.toString().endsWith(".sql"))
                    .sorted()
                    .toList()) {
                List<String> lines = Files.readAllLines(script);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher column = COLUMN.matcher(lines.get(i).replaceAll("--.*", ""));
                    if (!column.find() || NOT_A_TYPE.contains(column.group(1))) {
                        continue;
                    }
                    columns++;
                    if (!PORTABLE.contains(column.group(1))) {
                        foreign.add(script.getFileName() + ":" + (i + 1) + " " + column.group(1));
                    }
                }
            }
        }
        assertThat(columns).describedAs("the column definitions are read").isGreaterThan(500);
        assertThat(foreign).isEmpty();
    }
}
