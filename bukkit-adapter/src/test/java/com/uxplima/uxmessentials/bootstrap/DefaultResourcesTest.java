package com.uxplima.uxmessentials.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.adapter.outbound.config.ConfigurateConfigStore;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the extraction of the bundled default config files: on a fresh install every file lands and the
 * written {@code config.conf} parses back to the same defaults the code reads with its fallbacks, and on an
 * upgrade an existing file keeps every operator value while gaining the settings the update added. Guards two
 * regressions: a data folder that ships with only the database and no editable config, and a new setting that
 * exists only inside the jar so the operator never learns it is there.
 */
class DefaultResourcesTest {

    private static final java.util.logging.Logger JUL = java.util.logging.Logger.getAnonymousLogger();

    @Test
    void writesEveryBundledDefault(@TempDir Path dataFolder) {
        DefaultResources.writeInto(dataFolder, JUL, "test");

        assertThat(dataFolder.resolve("config.conf")).isRegularFile();
        assertThat(dataFolder.resolve("modules/communication/config.conf")).isRegularFile();
        assertThat(dataFolder.resolve("modules/communication/join-quit.conf")).isRegularFile();
        assertThat(dataFolder.resolve("modules/communication/announcer.conf")).isRegularFile();
        assertThat(dataFolder.resolve("modules/communication/info-pages.conf")).isRegularFile();
        assertThat(dataFolder.resolve("messages/messages_en.conf")).isRegularFile();
        assertThat(dataFolder.resolve("messages/messages_tr.conf")).isRegularFile();
    }

    @Test
    void writtenConfigParsesToTheDocumentedDefaults(@TempDir Path dataFolder) {
        DefaultResources.writeInto(dataFolder, JUL, "test");
        ConfigStore config = ConfigurateConfigStore.loadLayout(dataFolder, new NoopLogger());

        assertThat(config.getString("storage.backend", "?")).isEqualTo("sqlite");
        assertThat(config.getString("storage.file", "?")).isEqualTo("uxmessentials.db");
        assertThat(config.getString("messages.default-locale", "?")).isEqualTo("en");
        assertThat(config.getInt("modules.teleport.default-warmup", -1)).isEqualTo(3);
        assertThat(config.getInt("modules.homes.default-limit", -1)).isEqualTo(3);
        assertThat(config.getString("modules.economy.wallet.default-currency", "?"))
                .isEqualTo("coins");
        assertThat(config.getInt("modules.vaults.default-size", -1)).isEqualTo(6);
        assertThat(config.getBoolean("modules.teleport.enabled", false)).isTrue();
        // communication now ships enabled (inert by default): the display/communication contexts default on so a fresh
        // install shows a working experience out of the box.
        assertThat(config.getBoolean("modules.communication.enabled", false)).isTrue();
        assertThat(config.getBoolean("modules.scoreboard.enabled", false)).isTrue();
        assertThat(config.getBoolean("modules.tablist.enabled", false)).isTrue();
        assertThat(config.getBoolean("modules.staff.enabled", false)).isTrue();
        // nametags now ships on: its default renders one clean custom name with the vanilla name hidden under it.
        assertThat(config.getBoolean("modules.nametags.enabled", false)).isTrue();
        assertThat(config.getBoolean("modules.migration.enabled", true)).isFalse();
    }

    @Test
    void doesNotOverwriteAnExistingFile(@TempDir Path dataFolder) throws Exception {
        Path config = dataFolder.resolve("config.conf");
        Files.createDirectories(dataFolder);
        String operatorEdit = "storage { backend = \"mysql\" }\n";
        Files.writeString(config, operatorEdit);

        DefaultResources.writeInto(dataFolder, JUL, "test");

        // No baseline yet, so this enable only records what the current jar ships: an operator upgrading into
        // this behaviour must not have their file rewritten on the strength of a guess.
        assertThat(Files.readString(config)).isEqualTo(operatorEdit);
    }

    @Test
    void recordsTheShippedDefaultAsTheBaseline(@TempDir Path dataFolder) throws Exception {
        DefaultResources.writeInto(dataFolder, JUL, "test");

        Path baseline = dataFolder.resolve(DefaultResources.BASELINE_DIR).resolve("modules/survival/config.conf");
        assertThat(baseline).isRegularFile();
        assertThat(Files.readString(baseline))
                .isEqualTo(Files.readString(dataFolder.resolve("modules/survival/config.conf")));
    }

    @Test
    void appendsTheSettingsAnUpdateAddedToAnExistingFile(@TempDir Path dataFolder) throws Exception {
        // An install from an older version: their file, and the default it came from, knew only about storage.
        String oldDefault = "storage { backend = \"sqlite\" }\n";
        String operatorEdit = "storage { backend = \"mysql\" }\n";
        Files.createDirectories(dataFolder);
        Files.writeString(dataFolder.resolve("config.conf"), operatorEdit);
        Path baseline = dataFolder.resolve(DefaultResources.BASELINE_DIR).resolve("config.conf");
        Files.createDirectories(Objects.requireNonNull(baseline.getParent()));
        Files.writeString(baseline, oldDefault);

        DefaultResources.writeInto(dataFolder, JUL, "9.9.9");

        String upgraded = Files.readString(dataFolder.resolve("config.conf"));
        assertThat(upgraded).startsWith(operatorEdit);
        assertThat(upgraded).contains("9.9.9");
        // The settings the newer default ships are now in their file, and their own value still wins.
        ConfigStore config = ConfigurateConfigStore.loadLayout(dataFolder, new NoopLogger());
        assertThat(config.getString("storage.backend", "?")).isEqualTo("mysql");
        assertThat(config.getString("messages.default-locale", "?")).isEqualTo("en");
        // The baseline has moved on, so a restart appends nothing a second time.
        String afterFirstRun = Files.readString(dataFolder.resolve("config.conf"));
        DefaultResources.writeInto(dataFolder, JUL, "9.9.9");
        assertThat(Files.readString(dataFolder.resolve("config.conf"))).isEqualTo(afterFirstRun);
    }

    @Test
    void leavesAMalformedFileAloneAndTriesAgainNextTime(@TempDir Path dataFolder) throws Exception {
        String broken = "storage { backend = \n";
        Files.createDirectories(dataFolder);
        Files.writeString(dataFolder.resolve("config.conf"), broken);
        Path baseline = dataFolder.resolve(DefaultResources.BASELINE_DIR).resolve("config.conf");
        Files.createDirectories(Objects.requireNonNull(baseline.getParent()));
        Files.writeString(baseline, "storage { backend = \"sqlite\" }\n");

        DefaultResources.writeInto(dataFolder, JUL, "9.9.9");

        assertThat(Files.readString(dataFolder.resolve("config.conf"))).isEqualTo(broken);
        // The baseline stays behind, so fixing the syntax error and restarting still brings the new settings in.
        assertThat(Files.readString(baseline)).doesNotContain("default-locale");
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
}
