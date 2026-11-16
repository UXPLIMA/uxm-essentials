package com.uxplima.uxmessentials.persistence.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.persistence.jooq.DefaultSchema;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Meta;
import org.jooq.Queries;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/**
 * On MySQL and MariaDB a TEXT column holds 64 KB, so every column that stores serialized item stacks is widened there
 * by scripts only that backend reads. These tests hold the scripts to the shared schema: they parse as MySQL, they
 * touch real columns without changing whether a column takes NULL, and every text column of the schema is either
 * widened or named here as one that never holds items. A new text column fails the last test until its author decides
 * which it is.
 */
class MariadbItemColumnsTest {

    /** Text columns that hold no item stacks, so a TEXT column is wide enough on every backend. */
    private static final Set<String> PLAIN_TEXT = Set.of(
            "communication_announcement.lines",
            "guard_history.after_data",
            "guard_history.before_data",
            "guard_regions.data",
            "holograms.click_command",
            "holograms.head_texture",
            "menu_player_data.data_value",
            "moderation_ip_bans.reason_args",
            "moderation_mutes.reason_args",
            "moderation_sanction_history.reason_args",
            "moderation_tempbans.reason_args",
            "npc.display_name",
            "npc.skin_signature",
            "npc.skin_texture",
            "player_skins.texture_sign",
            "player_skins.texture_value",
            "staff_loadout.potion_effects",
            "trade_escrow.money");

    private static final Pattern WIDEN =
            Pattern.compile("ALTER TABLE \\$\\{prefix}(\\w+) MODIFY (\\w+) LONGTEXT( NOT NULL)?;");

    private static final DSLContext MYSQL = DSL.using(SQLDialect.MYSQL);

    @TempDir
    Path folder;

    @Test
    void onlyMariadbReadsTheVendorScripts() {
        assertThat(FlywayMigrationRunner.resolvedLocations(DatabaseBackend.MARIADB, List.of()))
                .containsExactly("classpath:db/migration", "classpath:db/vendor/mariadb");
        assertThat(FlywayMigrationRunner.resolvedLocations(DatabaseBackend.SQLITE, List.of()))
                .containsExactly("classpath:db/migration");
        assertThat(FlywayMigrationRunner.resolvedLocations(DatabaseBackend.POSTGRES, List.of("db/migration")))
                .containsExactly("classpath:db/migration");
    }

    @Test
    void theVendorScriptsTakeVersionsNoSharedScriptHolds() {
        List<String> shared = versions(DatabaseBackend.SQLITE);
        List<String> mariadb = versions(DatabaseBackend.MARIADB);

        assertThat(mariadb).containsAll(shared).contains("105").doesNotHaveDuplicates();
        assertThat(shared).doesNotContain("105");
    }

    @Test
    void everyVendorStatementParsesAsMysqlAndWidensOneColumn() {
        String script = vendorScripts();

        Queries parsed = MYSQL.parser().parse(bare(script));

        assertThat(parsed.queries()).hasSize(widened(script).size());
    }

    @Test
    void wideningKeepsEveryColumnAndWhetherItTakesNull() {
        Meta before = MYSQL.meta(DefaultSchema.DEFAULT_SCHEMA);

        Meta after = before.apply(MYSQL.parser().parse(bare(vendorScripts())));

        assertThat(nullability(after)).isEqualTo(nullability(before));
        for (Map.Entry<String, Boolean> column : widened(vendorScripts()).entrySet()) {
            assertThat(nullability(before)).containsEntry(column.getKey(), column.getValue());
        }
    }

    @Test
    void everyTextColumnIsWidenedOrNamedAsPlainText() {
        Set<String> widened = widened(vendorScripts()).keySet();
        Set<String> text = new HashSet<>();
        for (Table<?> table : DefaultSchema.DEFAULT_SCHEMA.getTables()) {
            for (Field<?> field : table.fields()) {
                if (field.getDataType().getSQLDataType() == SQLDataType.CLOB) {
                    text.add(table.getName() + "." + field.getName());
                }
            }
        }

        assertThat(widened).doesNotContainAnyElementsOf(PLAIN_TEXT);
        Set<String> covered = new HashSet<>(widened);
        covered.addAll(PLAIN_TEXT);
        assertThat(covered).isEqualTo(text);
    }

    private List<String> versions(DatabaseBackend backend) {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve(backend.name() + ".db"));
        Flyway flyway = Flyway.configure(getClass().getClassLoader())
                .dataSource(data)
                .locations(FlywayMigrationRunner.resolvedLocations(backend, List.of())
                        .toArray(String[]::new))
                .placeholders(Map.of("prefix", ""))
                .load();
        return Stream.of(flyway.info().all())
                .map(MigrationInfo::getVersion)
                .map(Object::toString)
                .toList();
    }

    /** Each widened column as table.column, mapped to whether it takes NULL after the script. */
    private static Map<String, Boolean> widened(String script) {
        Map<String, Boolean> columns = new HashMap<>();
        Matcher statement = WIDEN.matcher(script);
        while (statement.find()) {
            columns.put(statement.group(1) + "." + statement.group(2), statement.group(3) == null);
        }
        return columns;
    }

    private static Map<String, Boolean> nullability(Meta meta) {
        Map<String, Boolean> columns = new HashMap<>();
        for (Table<?> table : meta.getTables()) {
            for (Field<?> field : table.fields()) {
                columns.put(
                        table.getName() + "." + field.getName(),
                        field.getDataType().nullable());
            }
        }
        return columns;
    }

    private static String bare(String script) {
        return script.replace("${prefix}", "");
    }

    private static String vendorScripts() {
        URL folder = Objects.requireNonNull(
                MariadbItemColumnsTest.class.getClassLoader().getResource("db/vendor/mariadb"), "db/vendor/mariadb");
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
