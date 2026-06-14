package com.uxplima.uxmessentials.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards the architectural purity of the staff bounded context's core sources: no {@code org.bukkit},
 * {@code io.papermc.paper}, {@code net.kyori}, SLF4J, or uxmLib import may appear under
 * {@code core/.../staff/}. The full class-identity ArchUnit fences live in the bukkit-adapter; this is a
 * fast, source-level smoke check so a stray Bukkit import in the new context fails {@code :core:test}
 * immediately rather than later in the adapter build.
 */
class StaffPurityTest {

    private static final List<String> FORBIDDEN =
            List.of("org.bukkit", "io.papermc.paper", "net.kyori", "org.slf4j", "com.uxplima.uxmlib");

    @Test
    void staffCoreSourcesImportNoBukkitPaperKyoriOrUxmlib() throws IOException {
        Path root = staffSourceRoot();
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                for (String line : readLines(file)) {
                    String trimmed = line.strip();
                    if (!trimmed.startsWith("import ")) {
                        continue;
                    }
                    for (String banned : FORBIDDEN) {
                        if (trimmed.contains(banned)) {
                            violations.add(root.relativize(file) + ": " + trimmed);
                        }
                    }
                }
            });
        }
        assertThat(violations).as("forbidden imports in core staff sources").isEmpty();
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path staffSourceRoot() {
        Path fromModule = Path.of("src", "main", "java", "com", "uxplima", "uxmessentials", "staff");
        if (Files.isDirectory(fromModule)) {
            return fromModule;
        }
        Path fromRepoRoot = Path.of("core", "src", "main", "java", "com", "uxplima", "uxmessentials", "staff");
        if (Files.isDirectory(fromRepoRoot)) {
            return fromRepoRoot;
        }
        return fail("could not locate the staff core source root from "
                + Path.of("").toAbsolutePath());
    }
}
