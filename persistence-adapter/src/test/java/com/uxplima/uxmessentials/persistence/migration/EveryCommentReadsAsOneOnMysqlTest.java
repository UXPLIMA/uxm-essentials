package com.uxplima.uxmessentials.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every line comment in a migration is one MySQL and MariaDB read as a comment.
 *
 * <p>They read {@code --} as a comment only when whitespace follows it. Taking the long dash out of the scripts left
 * a comment starting {@code --,} in V26 and {@code --:} in V76, which SQLite and PostgreSQL still skip and MySQL parses
 * as SQL. So the plugin did not start on MySQL or MariaDB at all: V26 failed with a syntax error, and the add-ons
 * failed with it.
 */
class EveryCommentReadsAsOneOnMysqlTest {

    /** A {@code --} that neither ends the line nor is followed by whitespace. */
    private static final Pattern NOT_A_COMMENT_ON_MYSQL = Pattern.compile("--(?![\\s-]|$)");

    @Test
    @DisplayName("every -- is followed by whitespace")
    void everyCommentHasItsSpace() throws IOException {
        Path migrations = Path.of("src/main/resources/db/migration");
        List<String> broken = new ArrayList<>();
        int scripts = 0;
        try (Stream<Path> files = Files.list(migrations)) {
            for (Path script : files.filter(path -> path.toString().endsWith(".sql"))
                    .sorted()
                    .toList()) {
                scripts++;
                List<String> lines = Files.readAllLines(script);
                for (int i = 0; i < lines.size(); i++) {
                    if (NOT_A_COMMENT_ON_MYSQL.matcher(lines.get(i)).find()) {
                        broken.add(script.getFileName() + ":" + (i + 1) + " " + lines.get(i));
                    }
                }
            }
        }
        assertThat(scripts).describedAs("the migrations are read").isGreaterThan(80);
        assertThat(broken).isEmpty();
    }
}
