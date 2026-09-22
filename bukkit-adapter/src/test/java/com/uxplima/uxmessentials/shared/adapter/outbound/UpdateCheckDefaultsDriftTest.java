package com.uxplima.uxmessentials.shared.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.uxplima.uxmessentials.shared.adapter.outbound.config.ConfigurateConfigStore;
import com.uxplima.uxmessentials.shared.adapter.outbound.update.UpdateCheckSettings;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The update-check block is written twice: once in the shipped {@code config.conf} an operator reads, and once as
 * the per-key fallback {@code UpdateCheckSettings.from} uses when a key is absent. An operator who deletes a line,
 * or who upgrades from a version that never shipped one, gets the second copy while reading the first, so the two
 * have to say the same thing.
 *
 * <p>This asks only that they agree, not that either is right. Whether the address names a repository that exists
 * is a question about the family and not about this plugin, and {@code scripts/check-style.sh} answers it for every
 * repository at once: it caught this plugin spelling the product name where the repository slug belongs, in ten
 * files including the default below. A second guard here would be one rule kept in two places.
 */
class UpdateCheckDefaultsDriftTest {

    @Test
    @DisplayName("the shipped config.conf says what the code falls back to")
    void theShippedBlockAndTheCodeFallbacksAgree(@TempDir Path folder) throws IOException {
        UpdateCheckSettings shipped = UpdateCheckSettings.from(store(shippedConfig()));
        UpdateCheckSettings fallbacks = UpdateCheckSettings.from(store(emptyConfig(folder)));

        assertThat(shipped)
                .as("update-check is written in config.conf and again as the fallback in UpdateCheckSettings; an "
                        + "operator who deletes a key reads one and gets the other")
                .isEqualTo(fallbacks);
    }

    private static ConfigStore store(Path file) {
        return ConfigurateConfigStore.load(file, new SilentLogger());
    }

    private static Path shippedConfig() {
        return repoRoot().resolve("bukkit-adapter/src/main/resources/config.conf");
    }

    private static Path emptyConfig(Path folder) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, "", StandardCharsets.UTF_8);
        return file;
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (settings.gradle.kts)");
    }

    /** The store logs a line as it loads, and a test of the values has nothing to say about it. */
    private static final class SilentLogger implements Logger {

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
