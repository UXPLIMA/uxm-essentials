package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every file that draws a particle reads it through {@code BukkitRegistryKeys.readParticle}, which carries its data.
 *
 * <p>Four features resolved a particle by name and drew it with no data, and the server refuses 22 particles drawn
 * that way. A file that calls {@code spawnParticle} and never reads the particle with its data is the shape that did
 * it.
 */
final class EveryNamedParticleIsReadWithItsDataTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    @Test
    @DisplayName("every file that draws a particle reads it with its data")
    void everyDrawReadsItsData() throws IOException {
        List<String> bare = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (source.contains(".spawnParticle(") && !source.contains("readParticle(")) {
                    bare.add(file.getFileName().toString());
                }
            }
        }

        assertThat(bare)
                .describedAs("these draw a particle without reading its data")
                .isEmpty();
    }
}
