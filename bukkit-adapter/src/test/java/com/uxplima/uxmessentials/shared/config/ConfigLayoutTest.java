package com.uxplima.uxmessentials.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import com.uxplima.uxmessentials.shared.adapter.outbound.config.ConfigurateConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLayoutTest {

    /** A Logger that swallows output; tests only assert on resolved values. */
    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

    @Test
    void mountsModuleConfigAndSiblingFilesUnderModulesPath(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config.conf"), "storage { backend = \"sqlite\" }\n");
        Path teleport = Files.createDirectories(dir.resolve("modules/teleport"));
        Files.writeString(teleport.resolve("config.conf"), "enabled = true\ndefault-warmup = 7\n");
        Files.writeString(teleport.resolve("rtp.conf"), "min-radius = 250\nmax-radius = 9000\n");

        ConfigurateConfigStore store = ConfigurateConfigStore.loadLayout(dir, NOOP);

        assertThat(store.getString("storage.backend", "x")).isEqualTo("sqlite");
        assertThat(store.getBoolean("modules.teleport.enabled", false)).isTrue();
        assertThat(store.getInt("modules.teleport.default-warmup", 0)).isEqualTo(7);
        assertThat(store.getInt("modules.teleport.rtp.min-radius", 0)).isEqualTo(250);
        assertThat(store.getInt("modules.teleport.rtp.max-radius", 0)).isEqualTo(9000);
    }

    @Test
    void reloadRepicksUpModuleFileChanges(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config.conf"), "");
        Path homes = Files.createDirectories(dir.resolve("modules/homes"));
        Files.writeString(homes.resolve("config.conf"), "default-limit = 3\n");
        ConfigurateConfigStore store = ConfigurateConfigStore.loadLayout(dir, NOOP);
        assertThat(store.getInt("modules.homes.default-limit", 0)).isEqualTo(3);

        Files.writeString(homes.resolve("config.conf"), "default-limit = 9\n");
        store.reload();
        assertThat(store.getInt("modules.homes.default-limit", 0)).isEqualTo(9);
    }

    @Test
    void malformedReloadKeepsTheLastKnownGoodTree(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config.conf"), "storage { backend = \"sqlite\" }\n");
        ConfigurateConfigStore store = ConfigurateConfigStore.loadLayout(dir, NOOP);

        Files.writeString(dir.resolve("config.conf"), "storage { backend = \"broken\"\n");

        assertThatThrownBy(store::reload).isInstanceOf(IllegalStateException.class);
        assertThat(store.getString("storage.backend", "missing")).isEqualTo("sqlite");
    }

    @Test
    void legacyMonolithRootStillResolvesModulePaths(@TempDir Path dir) throws Exception {
        // No modules/ dir; everything inline in config.conf (old layout) still works.
        Files.writeString(dir.resolve("config.conf"), "modules { warps { enabled = true } }\n");
        ConfigurateConfigStore store = ConfigurateConfigStore.loadLayout(dir, NOOP);
        assertThat(store.getBoolean("modules.warps.enabled", false)).isTrue();
    }
}
