package com.uxplima.uxmessentials.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Guards the per-module config layout: the root {@code config.conf} carries no module tunables (those moved
 * under {@code modules/}), and every bounded context ships a {@code modules/<module>/config.conf} resource so
 * the first-run extraction lands a complete tree. The first-run-tree resolution itself is exercised next to
 * {@code DefaultResources} (where its package-private writer is visible).
 */
class ConfigLayoutDriftTest {

    @Test
    void rootConfigCarriesNoModuleTunables() throws Exception {
        String root = Files.readString(resources().resolve("config.conf"));
        assertThat(root)
                .doesNotContain("default-warmup")
                .doesNotContain("give-cap")
                .doesNotContain("default-limit");
    }

    @Test
    void everyModuleShipsAConfigResource() {
        Path modules = resources().resolve("modules");
        for (String module : new String[] {
            "teleport",
            "homes",
            "warps",
            "economy",
            "kits",
            "playerstate",
            "messaging",
            "presence",
            "moderation",
            "itemworld",
            "vaults",
            "communication",
            "migration"
        }) {
            assertThat(modules.resolve(module).resolve("config.conf"))
                    .as(module + " config resource")
                    .exists();
        }
    }

    /** The bundled resource root, located by walking up to the repo and descending into the adapter module. */
    static Path resources() {
        Path repoRoot = repoRoot();
        return repoRoot.resolve("bukkit-adapter/src/main/resources");
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
}
