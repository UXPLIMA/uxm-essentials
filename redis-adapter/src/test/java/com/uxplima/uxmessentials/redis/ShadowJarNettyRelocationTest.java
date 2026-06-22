package com.uxplima.uxmessentials.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Build-artifact guard: the {@code uxmEssentials-redis} shadow jar must ship Netty <em>relocated</em> under
 * {@code com.uxplima.uxmessentials.libs.netty} so it never clashes with Paper's own (differently-versioned)
 * bundled Netty when an operator drops this jar on a backend. {@code :redis-adapter:test} depends on
 * {@code shadowJar} (see build.gradle.kts) so the jar exists when this runs; the {@code assumeTrue} guard keeps
 * the test honest if invoked in isolation without the jar.
 */
class ShadowJarNettyRelocationTest {

    @Test
    void shadow_jar_relocates_netty_and_keeps_lettuce_in_place() throws IOException {
        Path jar = findShadowJar();
        if (jar == null) {
            assumeTrue(false, "uxmEssentials-redis shadow jar not built — skipping (run :redis-adapter:shadowJar)");
            return;
        }

        List<String> entries = entryNames(jar);

        assertThat(entries)
                .as("Netty must be relocated, not shipped at io/netty (would clash with Paper's Netty)")
                .noneMatch(p -> p.startsWith("io/netty/"));
        assertThat(entries)
                .as("Netty must be present under the relocated package")
                .anyMatch(p -> p.startsWith("com/uxplima/uxmessentials/libs/netty/"));
        assertThat(entries)
                .as("Lettuce itself stays at io/lettuce (only its transitive Netty/Reactor move)")
                .anyMatch(p -> p.startsWith("io/lettuce/"));
        assertThat(entries)
                .as("our own adapter classes ship un-relocated")
                .anyMatch(p -> p.startsWith("com/uxplima/uxmessentials/redis/"));
    }

    @Test
    void shadow_jar_relocates_the_transitive_uxmlib_toolkit() throws IOException {
        // :core depends on uxmlib-redis (and its uxmlib-common transitive), so this companion shades the toolkit
        // transitively. It must be relocated to the same coordinates the main jar uses (never shipped bare at
        // com/uxplima/uxmlib), or it would clash on a backend with another plugin that bundles uxmlib.
        Path jar = findShadowJar();
        if (jar == null) {
            assumeTrue(false, "uxmEssentials-redis shadow jar not built — skipping (run :redis-adapter:shadowJar)");
            return;
        }

        List<String> entries = entryNames(jar);

        assertThat(entries)
                .as("uxmlib must be relocated, not shipped bare at com/uxplima/uxmlib")
                .noneMatch(p -> p.startsWith("com/uxplima/uxmlib/"));
        assertThat(entries)
                .as("uxmlib must be present under the relocated package")
                .anyMatch(p -> p.startsWith("com/uxplima/uxmessentials/libs/uxmlib/"));
    }

    @Nullable private static Path findShadowJar() throws IOException {
        Path libs = Path.of("build", "libs");
        if (!Files.isDirectory(libs)) {
            return null;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(libs, "uxmEssentials-redis*.jar")) {
            for (Path candidate : stream) {
                return candidate;
            }
        }
        return null;
    }

    private static List<String> entryNames(Path jar) throws IOException {
        List<String> names = new ArrayList<>();
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
        }
        return names;
    }
}
