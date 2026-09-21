package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The descriptor-drift guard: every jar declares the server it was built for.
 *
 * <p>{@code api-version} is what the server reads to decide whether a plugin is too old to load and
 * whether its materials need remapping. Twenty six plugins across the estate said {@code 1.21} while
 * compiling against Paper 26.2, which is a claim the bytecode cannot keep: a 1.21 server believes the
 * line, loads the jar, and throws {@code NoSuchMethodError} at the first call into an API it does not
 * have. This product was the one that had it right in all five descriptors, and this is what keeps it
 * right the day the platform moves.
 *
 * <p>The platform is not written twice. It is read out of {@code gradle/libs.versions.toml}, which is the
 * one place a version is written.
 */
class DescriptorPlatformDriftTest {

    /** {@code paper = "26.2.build.112-stable"}, of which the first two parts are the platform. */
    private static final Pattern PAPER = Pattern.compile("(?m)^paper\\s*=\\s*\"(\\d+)\\.(\\d+)");

    private static final Pattern DECLARED = Pattern.compile("(?m)^api-version:\\s*'([^']+)'");

    @Test
    @DisplayName("every descriptor names the platform the build compiles against")
    void everydescriptorNamesThePlatform() {
        Optional<String> platform = platform();

        assertThat(platform)
                .describedAs("the catalogue names the Paper this build takes, and this guard cannot read"
                        + " it. Either the key moved or the pattern is wrong.")
                .isPresent();

        List<String> wrong = new ArrayList<>();
        for (Path file : descriptors()) {
            Matcher version = DECLARED.matcher(read(file));
            String declared = version.find() ? version.group(1) : "none";
            if (!declared.equals(platform.orElseThrow())) {
                wrong.add(repoRoot().relativize(file) + " declares " + declared);
            }
        }

        assertThat(wrong)
                .describedAs(
                        "A descriptor that claims an older server than the jar was built for is a"
                                + " promise the bytecode cannot keep. The platform is %s.",
                        platform.orElse("?"))
                .isEmpty();
    }

    @Test
    @DisplayName("the descriptors are found, so this cannot pass by reading nothing")
    void thedescriptorsAreFound() {
        assertThat(descriptors())
                .describedAs("this build ships plugin descriptors, so finding none is a broken scan")
                .hasSizeGreaterThan(1);
    }

    private static Optional<String> platform() {
        Matcher paper = PAPER.matcher(read(repoRoot().resolve("gradle/libs.versions.toml")));
        return paper.find() ? Optional.of(paper.group(1) + "." + paper.group(2)) : Optional.empty();
    }

    private static List<Path> descriptors() {
        try (Stream<Path> files = Files.walk(repoRoot())) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("paper-plugin.yml"))
                    .filter(path -> !path.toString().contains("/build/"))
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not walk " + repoRoot(), unreadable);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + path, unreadable);
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "could not locate the repository root from " + Path.of("").toAbsolutePath());
    }
}
