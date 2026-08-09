package com.uxplima.uxmessentials.drift;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Keeps the set of modules that publish a Maven artifact in lockstep with the set the developer docs tell
 * people to depend on. A module that starts publishing without a documented coordinate is invisible to every
 * reader; a documented coordinate with no publication is a broken copy-paste for every reader. Both halves
 * are cheap to get wrong and expensive to notice, so they are pinned here rather than left to review.
 */
class PublishedArtifactsDriftTest {

    /** The modules that are a published compatibility promise. Adding one means adding its docs page too. */
    private static final Set<String> PUBLISHED = Set.of("api", "bukkit-api");

    /** Every module in the build, so a new publishing module cannot slip in unlisted. */
    private static final Set<String> ALL_MODULES = Set.of(
            "api",
            "bukkit-api",
            "core",
            "bukkit-adapter",
            "persistence-adapter",
            "migration",
            "velocity-adapter",
            "discord-adapter",
            "redis-adapter");

    private static final String CONVENTION = "uxmessentials.publish-conventions";

    @Test
    void everyPublishedModuleAppliesThePublishConvention() throws IOException {
        for (String module : PUBLISHED) {
            assertThat(buildFileOf(module))
                    .as("%s is a published artifact, so it must apply %s", module, CONVENTION)
                    .contains(CONVENTION);
        }
    }

    @Test
    void noOtherModulePublishesQuietly() throws IOException {
        for (String module : ALL_MODULES) {
            if (PUBLISHED.contains(module)) {
                continue;
            }
            assertThat(buildFileOf(module))
                    .as("%s is not a published artifact; adding it to PUBLISHED also means documenting it", module)
                    .doesNotContain(CONVENTION);
        }
    }

    @Test
    void theModuleListMatchesTheBuild() throws IOException {
        String settings = Files.readString(repositoryRoot().resolve("settings.gradle.kts"));
        List<String> declared = settings.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("\":") && line.endsWith("\","))
                .map(line -> line.substring(2, line.length() - 2))
                .toList();

        assertThat(declared)
                .as("ALL_MODULES must list every module settings.gradle.kts includes")
                .containsExactlyInAnyOrderElementsOf(ALL_MODULES);
    }

    private static String buildFileOf(String module) throws IOException {
        return Files.readString(repositoryRoot().resolve(module).resolve("build.gradle.kts"));
    }

    /** The build's root, found by walking up to the settings file, as the other drift guards do. */
    private static Path repositoryRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "settings.gradle.kts not found above " + Path.of("").toAbsolutePath());
    }
}
