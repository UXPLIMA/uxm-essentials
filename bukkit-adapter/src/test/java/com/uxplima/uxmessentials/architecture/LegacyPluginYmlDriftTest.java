package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The manifest guard (CLAUDE.md §3 "no plugin.yml, use paper-plugin.yml", docs/03-paper-api.md §2).
 *
 * <p><strong>The bug this freezes out.</strong> Paper loads a {@code plugin.yml} in preference to a
 * {@code paper-plugin.yml}, silently. A jar that ships both loads through the legacy path, which has no
 * {@code PluginBootstrap} and no {@code PluginLoader}: the bootstrap never runs, the Brigadier registration
 * lifecycle never fires, and the plugin comes up looking enabled while most of it is not wired. That failure
 * reads as "commands are missing" rather than "the wrong manifest was picked", which is a long afternoon.
 *
 * <p><strong>The invariant.</strong> No module's resources carry a {@code plugin.yml}. The one manifest is
 * {@code bukkit-adapter/src/main/resources/paper-plugin.yml}, and the Velocity jar carries its own annotation-based
 * descriptor instead.
 */
class LegacyPluginYmlDriftTest {

    /**
     * The modules whose resources ship inside a jar. Competitor sources under {@code rakipler/} are study material
     * that is never built or published, so their manifests are none of this guard's business.
     */
    private static final List<String> MODULES =
            List.of("bukkit-adapter", "velocity-adapter", "redis-adapter", "discord-adapter", "rest-adapter");

    @Test
    void noModuleShipsALegacyPluginManifest() {
        List<String> found = new ArrayList<>();
        Path root = ProductionSources.repoRoot();
        for (String module : MODULES) {
            Path resources = root.resolve(module).resolve("src/main/resources");
            if (!Files.isDirectory(resources)) {
                continue;
            }
            try (Stream<Path> tree = Files.walk(resources)) {
                tree.filter(path -> path.getFileName().toString().equals("plugin.yml"))
                        .forEach(path -> found.add(root.relativize(path).toString()));
            } catch (IOException e) {
                throw new UncheckedIOException("could not walk " + resources, e);
            }
        }
        assertThat(found)
                .as("Paper prefers plugin.yml over paper-plugin.yml silently, and the legacy path runs no"
                        + " PluginBootstrap: the plugin would enable with most of itself unwired")
                .isEmpty();
    }

    @Test
    void thePaperManifestIsWhereTheBootstrapExpectsIt() {
        Path manifest = ProductionSources.repoRoot().resolve("bukkit-adapter/src/main/resources/paper-plugin.yml");
        assertThat(manifest).as("the one manifest the shaded jar ships").exists();
    }
}
