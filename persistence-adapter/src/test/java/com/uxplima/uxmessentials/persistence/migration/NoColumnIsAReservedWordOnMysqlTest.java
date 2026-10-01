package com.uxplima.uxmessentials.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A column named with a word MySQL reserves is quoted, and the migrations read the quote as a name on MySQL too.
 *
 * <p>V65 named a column {@code lines}, which MySQL and MariaDB reserve, so the plugin stopped at V65 on both. A name
 * in double quotes is the same column on SQLite and PostgreSQL, and MySQL reads it as one under {@code ANSI_QUOTES},
 * which the migration runner turns on for its own connections there and nowhere else.
 */
class NoColumnIsAReservedWordOnMysqlTest {

    /** The MySQL 8 reserved words a column is likely to be named after. */
    private static final Set<String> RESERVED = Set.of(
            "add",
            "all",
            "alter",
            "analyze",
            "and",
            "as",
            "asc",
            "before",
            "between",
            "both",
            "by",
            "call",
            "cascade",
            "case",
            "change",
            "check",
            "column",
            "condition",
            "constraint",
            "continue",
            "convert",
            "create",
            "cross",
            "cube",
            "current_date",
            "current_time",
            "current_timestamp",
            "current_user",
            "cursor",
            "database",
            "default",
            "delayed",
            "delete",
            "desc",
            "describe",
            "distinct",
            "div",
            "drop",
            "dual",
            "each",
            "else",
            "empty",
            "escaped",
            "except",
            "exists",
            "exit",
            "explain",
            "false",
            "fetch",
            "for",
            "force",
            "foreign",
            "from",
            "function",
            "generated",
            "get",
            "grant",
            "group",
            "groups",
            "having",
            "if",
            "ignore",
            "in",
            "index",
            "inner",
            "insert",
            "interval",
            "into",
            "is",
            "join",
            "key",
            "keys",
            "kill",
            "lag",
            "lead",
            "leading",
            "leave",
            "left",
            "like",
            "limit",
            "linear",
            "lines",
            "load",
            "lock",
            "long",
            "loop",
            "match",
            "member",
            "mod",
            "natural",
            "not",
            "null",
            "of",
            "on",
            "option",
            "or",
            "order",
            "out",
            "outer",
            "over",
            "partition",
            "primary",
            "procedure",
            "purge",
            "range",
            "rank",
            "read",
            "reads",
            "recursive",
            "references",
            "regexp",
            "release",
            "rename",
            "repeat",
            "replace",
            "require",
            "restrict",
            "return",
            "revoke",
            "right",
            "rlike",
            "row",
            "rows",
            "schema",
            "select",
            "separator",
            "set",
            "show",
            "signal",
            "spatial",
            "sql",
            "ssl",
            "starting",
            "stored",
            "system",
            "table",
            "terminated",
            "then",
            "to",
            "trailing",
            "trigger",
            "true",
            "undo",
            "union",
            "unique",
            "unlock",
            "update",
            "usage",
            "use",
            "using",
            "values",
            "virtual",
            "when",
            "where",
            "while",
            "window",
            "with",
            "write",
            "xor");

    /** A column definition: a bare name at the start of a line, then a type. */
    private static final Pattern COLUMN = Pattern.compile(
            "^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s+(VARCHAR|TEXT|BIGINT|INTEGER|INT|BOOLEAN|SMALLINT|DOUBLE|REAL|BLOB"
                    + "|DECIMAL|NUMERIC|CHAR|TIMESTAMP|DATE|FLOAT)\\b",
            Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("no bare column name is a word MySQL reserves")
    void noBareColumnIsReserved() throws IOException {
        List<String> reserved = new ArrayList<>();
        int columns = 0;
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path script : files.filter(path -> path.toString().endsWith(".sql"))
                    .sorted()
                    .toList()) {
                List<String> lines = Files.readAllLines(script);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher column = COLUMN.matcher(lines.get(i).replaceAll("--.*", ""));
                    if (!column.find()) {
                        continue;
                    }
                    columns++;
                    if (RESERVED.contains(column.group(1).toLowerCase(Locale.ROOT))) {
                        reserved.add(script.getFileName() + ":" + (i + 1) + " " + column.group(1));
                    }
                }
            }
        }
        assertThat(columns).describedAs("the column definitions are read").isGreaterThan(300);
        assertThat(reserved).isEmpty();
    }

    @Test
    @DisplayName("the runner reads a double-quoted name as a name on MySQL, and changes nothing elsewhere")
    void mysqlReadsTheQuoteAsAName() {
        assertThat(FlywayMigrationRunner.initSql(DatabaseBackend.MARIADB)).contains("ANSI_QUOTES");
        assertThat(FlywayMigrationRunner.initSql(DatabaseBackend.SQLITE)).isEmpty();
        assertThat(FlywayMigrationRunner.initSql(DatabaseBackend.POSTGRES)).isEmpty();
    }
}
