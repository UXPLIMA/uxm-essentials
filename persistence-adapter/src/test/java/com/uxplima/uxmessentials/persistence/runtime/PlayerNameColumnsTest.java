package com.uxplima.uxmessentials.persistence.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.persistence.jooq.DefaultSchema;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

/**
 * Every column that holds a player's or an issuer's name is sixty-four characters on every backend, so a Floodgate
 * name longer than a Java one is stored whole, and the length a write is fitted to is that one.
 */
class PlayerNameColumnsTest {

    /** The columns that hold a player's or an issuer's name. */
    private static final Set<String> NAME_COLUMNS = Set.of(
            "economy_owners.name",
            "mail.sender_name",
            "moderation_mutes.muted_by_name",
            "moderation_jails.jailed_by_name",
            "moderation_tempbans.banned_by_name",
            "moderation_warns.warned_by_name",
            "moderation_ip_bans.banned_by_name",
            "moderation_seen.name",
            "moderation_sanction_history.actor_name",
            "player_names.name",
            "player_names.lower_name",
            "guard_denials.player",
            "board_name.name",
            "player_warps.owner_name");

    private static final Pattern POSTGRES_WIDEN =
            Pattern.compile("ALTER TABLE \\$\\{prefix}(\\w+) ALTER COLUMN (\\w+) TYPE VARCHAR\\(64\\);");

    @Test
    void theWritesAreFittedToSixtyFourCharacters() {
        Set<String> found = new HashSet<>();
        for (Table<?> table : DefaultSchema.DEFAULT_SCHEMA.getTables()) {
            for (Field<?> field : table.fields()) {
                String column = table.getName() + "." + field.getName();
                if (NAME_COLUMNS.contains(column)) {
                    assertThat(field.getDataType().length()).as(column).isEqualTo(64);
                    found.add(column);
                }
            }
        }
        assertThat(found).isEqualTo(NAME_COLUMNS);
    }

    @Test
    void mysqlMariadbAndPostgresqlWidenTheSameColumns() {
        assertThat(MariadbItemColumnsTest.widenedNames(MariadbItemColumnsTest.vendorScripts())
                        .keySet())
                .isEqualTo(NAME_COLUMNS);
        Set<String> postgres = new HashSet<>();
        Matcher statement = POSTGRES_WIDEN.matcher(postgresScripts());
        while (statement.find()) {
            postgres.add(statement.group(1) + "." + statement.group(2));
        }
        assertThat(postgres).isEqualTo(NAME_COLUMNS);
    }

    @Test
    void thePostgresqlScriptParsesAsPostgresql() {
        String script = postgresScripts();

        assertThat(DSL.using(SQLDialect.POSTGRES)
                        .parser()
                        .parse(script.replace("${prefix}", ""))
                        .queries())
                .hasSize(NAME_COLUMNS.size());
    }

    @Test
    void aUuidStandingInForANameIsNeverWrittenAsOne() {
        Field<String> column = DSL.field("name", org.jooq.impl.SQLDataType.VARCHAR(64));

        assertThat(ColumnFit.name(column, "069a79f4-44e9-4726-a5be-fca90e38aaf5"))
                .isEmpty();
        assertThat(ColumnFit.name(column, ".Twenty_Char_Gamer_1")).isEqualTo(".Twenty_Char_Gamer_1");
        assertThat(ColumnFit.name(column, "x".repeat(70))).hasSize(64);
    }

    private static String postgresScripts() {
        URL folder = Objects.requireNonNull(
                PlayerNameColumnsTest.class.getClassLoader().getResource("db/vendor/postgresql"),
                "db/vendor/postgresql");
        try (Stream<Path> files = Files.list(Path.of(folder.toURI()))) {
            StringBuilder script = new StringBuilder();
            for (Path file : files.sorted().toList()) {
                script.append(Files.readString(file, StandardCharsets.UTF_8)).append('\n');
            }
            return script.toString();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        } catch (URISyntaxException malformed) {
            throw new IllegalStateException(malformed);
        }
    }
}
