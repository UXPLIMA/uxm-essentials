package com.uxplima.uxmessentials.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import com.uxplima.uxmessentials.persistence.runtime.DataSourceFactory;
import com.uxplima.uxmessentials.persistence.runtime.DatabaseSettings;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * V85 removes the Discord account-link tables, with whatever a server had written to them.
 *
 * <p>Account linking left this plugin on 2026-10-01: the owner sells it as a plugin of its own. The owner chose to
 * delete the bindings rather than leave two tables nothing reads. V16 that made them stays, because Flyway refuses
 * to start on a database that has applied a migration the jar no longer carries.
 */
class DiscordLinksDropMigrationTest {

    private HikariDataSource dataSource;
    private DSLContext dsl;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        dataSource = DataSourceFactory.create(new DatabaseSettings(new SqliteConfig(), dataFolder));
        dsl = DSL.using(dataSource, SQLDialect.SQLITE);
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    @DisplayName("a server that had linked players loses both tables, and the rows in them, at V85")
    void bothTablesAreGone() {
        migrateTo("84");
        dsl.execute("INSERT INTO discord_links (player, discord_id, linked_at) VALUES ("
                + "'00000000-0000-0000-0000-000000000001', '123456789012345678', 1700000000000)");
        dsl.execute("INSERT INTO discord_link_pending (player, code, expires_at) VALUES ("
                + "'00000000-0000-0000-0000-000000000002', 'ABC123', 1700000000000)");
        assertThat(tables()).contains("discord_links", "discord_link_pending");

        migrateTo("85");

        assertThat(tables()).doesNotContain("discord_links", "discord_link_pending");
    }

    private java.util.List<String> tables() {
        return dsl.fetch("SELECT name FROM sqlite_master WHERE type = 'table'").getValues(0, String.class);
    }

    private void migrateTo(String version) {
        Flyway.configure(getClass().getClassLoader())
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
    }

    /** A config that selects the embedded SQLite backend with every default: no network coordinates. */
    private record SqliteConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }
}
