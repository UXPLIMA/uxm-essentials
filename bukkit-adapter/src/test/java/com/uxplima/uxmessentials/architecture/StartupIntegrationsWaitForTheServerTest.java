package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A plugin that loads at {@code STARTUP} resolves its integrations when the server has loaded, not while it enables.
 *
 * <p>This plugin loads at {@code STARTUP}, so it enables before every ordinary plugin, and {@code load: BEFORE} in
 * {@code paper-plugin.yml} cannot reach across load phases. Resolved while it enabled, the Vault economy and
 * permission integrations found Vault installed but not enabled, took the no-op, and kept it for the whole run: on
 * 2026-09-23 a stand-in Vault on the oneblock server enabled thirteen seconds after this plugin had resolved. uxmLib's
 * {@code Integrations.resolveWhenLoaded} resolves again on {@code ServerLoadEvent}, behind the same capability.
 */
final class StartupIntegrationsWaitForTheServerTest {

    private static final Path DESCRIPTOR = Path.of("src", "main", "resources", "paper-plugin.yml");
    private static final Path SOURCE = Path.of("src", "main", "java");

    @Test
    @DisplayName("a STARTUP plugin resolves its integrations once the server has loaded")
    void startupIntegrationsWaitForTheServer() throws IOException {
        assertThat(Files.readString(DESCRIPTOR, StandardCharsets.UTF_8))
                .describedAs("this guard exists because the plugin loads at STARTUP")
                .contains("load: STARTUP");

        List<String> early = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (Files.readString(file, StandardCharsets.UTF_8).contains("Integrations.resolve(")) {
                    early.add(file.getFileName().toString());
                }
            }
        }

        assertThat(early)
                .describedAs("Integrations.resolve binds while this plugin enables, before Vault has; use"
                        + " Integrations.resolveWhenLoaded")
                .isEmpty();
    }
}
