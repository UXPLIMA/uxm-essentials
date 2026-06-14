package com.uxplima.uxmessentials.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import com.uxplima.uxmessentials.shared.adapter.outbound.config.ConfigurateConfigStore;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the first-run extraction of the bundled default config files: every file lands, the written
 * {@code config.conf} parses back to the same defaults the code reads with its fallbacks, and an existing
 * file is never overwritten. Guards against a regression where the data folder ships with only the
 * database and no editable config.
 */
class DefaultResourcesTest {

    private static final java.util.logging.Logger JUL = java.util.logging.Logger.getAnonymousLogger();

    @Test
    void writesEveryBundledDefault(@TempDir Path dataFolder) {
        DefaultResources.writeInto(dataFolder, JUL);

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
        DefaultResources.writeInto(dataFolder, JUL);
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
        // nametags stays off: its default renders a custom name above the vanilla one until name-hiding lands.
        assertThat(config.getBoolean("modules.nametags.enabled", true)).isFalse();
        assertThat(config.getBoolean("modules.migration.enabled", true)).isFalse();
    }

    @Test
    void doesNotOverwriteAnExistingFile(@TempDir Path dataFolder) throws Exception {
        Path config = dataFolder.resolve("config.conf");
        Files.createDirectories(dataFolder);
        String operatorEdit = "storage { backend = \"mysql\" }\n";
        Files.writeString(config, operatorEdit);

        DefaultResources.writeInto(dataFolder, JUL);

        assertThat(Files.readString(config)).isEqualTo(operatorEdit);
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
