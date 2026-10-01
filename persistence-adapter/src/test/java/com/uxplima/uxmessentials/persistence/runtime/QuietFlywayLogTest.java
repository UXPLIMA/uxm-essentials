package com.uxplima.uxmessentials.persistence.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.flywaydb.core.api.logging.Log;
import org.flywaydb.core.api.logging.LogFactory;
import org.flywaydb.core.internal.logging.EvolvingLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Flyway's log, without the note MariaDB writes for every named primary key.
 *
 * <p>The migrations name their primary keys, MariaDB ignores the name and says so as a warning, and a first start on
 * MariaDB printed seventy six of them. An operator read a wall of WARN as a failed install. Every other line, the
 * warnings included, still goes through.
 */
class QuietFlywayLogTest {

    @Test
    @DisplayName("the ignored primary key name is dropped, and every other line reaches the log")
    void onlyTheIgnoredKeyNameIsDropped() {
        List<String> lines = new ArrayList<>();
        Log log = QuietFlywayLog.around(new Recording(lines));

        log.warn("DB: Name 'pk_homes' ignored for PRIMARY key. (SQL State:  - Error Code: 1280)");
        log.warn("DB: Data truncated for column 'x' at row 1 (SQL State: 01000 - Error Code: 1265)");
        log.info("Migrating schema `uxmessentials` to version \"1 - init\"");
        log.error("Migration failed");

        assertThat(lines)
                .containsExactly(
                        "warn DB: Data truncated for column 'x' at row 1 (SQL State: 01000 - Error Code: 1265)",
                        "info Migrating schema `uxmessentials` to version \"1 - init\"",
                        "error Migration failed");
    }

    /**
     * The runner hands Flyway the log before it migrates. Naming the class instead had Flyway look the name up in its
     * own loader, which cannot see this plugin's classes, and the plugin did not start; setting it before the Flyway
     * was built had it reset.
     */
    @Test
    @DisplayName("after a migration Flyway logs through it")
    void theRunnerUsesIt(@TempDir Path dataFolder) {
        Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger())
                .close();

        EvolvingLog flywayLog = (EvolvingLog) LogFactory.getLog(QuietFlywayLogTest.class);
        assertThat(flywayLog.getLog().getClass().getEnclosingClass()).isEqualTo(QuietFlywayLog.class);
    }

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

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    private record Recording(List<String> lines) implements Log {
        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void debug(String message) {
            lines.add("debug " + message);
        }

        @Override
        public void info(String message) {
            lines.add("info " + message);
        }

        @Override
        public void warn(String message) {
            lines.add("warn " + message);
        }

        @Override
        public void error(String message) {
            lines.add("error " + message);
        }

        @Override
        public void error(String message, Exception failure) {
            lines.add("error " + message);
        }

        @Override
        public void notice(String message) {
            lines.add("notice " + message);
        }
    }
}
