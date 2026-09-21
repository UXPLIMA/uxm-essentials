package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The independence-drift guard: no plugin of ours depends on another plugin of ours.
 *
 * <p>It is the oldest rule in the estate and the one a customer feels first. Every plugin is sold on its
 * own and has to work as the only plugin on a server, so the only thing they share is uxmLib. An optional
 * link is allowed and a dependency is not: a plugin may read what another publishes <em>if it happens to
 * be there</em>, through a marked service registration, never a load order and never a line that stops
 * this one working when the other is absent.
 *
 * <p>Two ways to break it, and both are checked across every module of this build.
 *
 * <ul>
 *   <li>A class of ours imports a class of another plugin of ours. That is a link error at load on every
 *       server that bought one product and not the other, before a line of ours runs.
 *   <li>A descriptor declares another plugin of ours as {@code required: true}. A companion jar of this
 *       product may require its host: {@code uxmEssentials-rest} and {@code uxmEssentials-redis} are this
 *       product in another jar, and an operator who has one has the other by definition. Anything else is
 *       a second product the operator would have to buy.
 * </ul>
 */
class PluginIndependenceDriftTest {

    /** This product's own root package. Everything under it, in any module, is itself. */
    private static final String OURS = "com.uxplima.uxmessentials.";

    /** The library, which every plugin is built on and which is never installed as a plugin beside us. */
    private static final String LIBRARY = "com.uxplima.uxmlib.";

    private static final Pattern IMPORT = Pattern.compile("(?m)^import\\s+(?:static\\s+)?(com\\.uxplima\\.[^;]+);");

    /** A declared dependency, which sits four spaces in, and the lines under it. */
    private static final Pattern DEPENDENCY =
            Pattern.compile("(?m)^ {4}([A-Za-z][A-Za-z0-9_-]*):\\s*$((?:\\n {6}.*)*)");

    /** The name a descriptor gives its own jar. */
    private static final Pattern OWN_NAME = Pattern.compile("(?m)^name:\\s*(\\S+)");

    @Test
    @DisplayName("nothing in any module imports a class of another plugin of ours")
    void nothingImportsAnotherPlugin() {
        List<String> borrowed = new ArrayList<>();
        for (Path file : filesUnder(repoRoot(), ".java")) {
            Matcher imported = IMPORT.matcher(read(file));
            while (imported.find()) {
                String named = imported.group(1);
                if (!named.startsWith(OURS) && !named.startsWith(LIBRARY)) {
                    borrowed.add(repoRoot().relativize(file) + " imports " + named);
                }
            }
        }

        assertThat(borrowed)
                .describedAs("A plugin that names another plugin's class dies at link time on the server"
                        + " that bought one and not the other. Read it through a service registration if it"
                        + " happens to be there, or write our own.")
                .isEmpty();
    }

    @Test
    @DisplayName("no descriptor requires a plugin of ours that is not this product")
    void nodescriptorRequiresAnotherProduct() {
        List<String> required = new ArrayList<>();
        for (Path file : descriptors()) {
            String descriptor = read(file);
            Matcher declared = DEPENDENCY.matcher(descriptor);
            while (declared.find()) {
                String named = declared.group(1);
                if (named.toLowerCase(Locale.ROOT).startsWith("uxm")
                        && declared.group(2).contains("required: true")
                        && !isOwnHost(descriptor, named)) {
                    required.add(repoRoot().relativize(file) + " requires " + named);
                }
            }
        }

        assertThat(required)
                .describedAs("A plugin of ours declared required is a product the operator has to buy to run"
                        + " this one. Declare it with required: false, or not at all.")
                .isEmpty();
    }

    @Test
    @DisplayName("both sides are read, so this cannot pass by finding nothing")
    void bothsidesAreRead() {
        assertThat(filesUnder(repoRoot(), ".java"))
                .describedAs("a source scan that finds no Java in this build is a broken scan")
                .hasSizeGreaterThan(100);
        assertThat(descriptors())
                .describedAs("this build ships plugin descriptors, so finding none is a broken scan")
                .hasSizeGreaterThan(1);
    }

    /** Whether the required name is this jar's own host rather than another product. */
    private static boolean isOwnHost(String descriptor, String named) {
        Matcher own = OWN_NAME.matcher(descriptor);
        if (!own.find()) {
            return false;
        }
        String mine = own.group(1).toLowerCase(Locale.ROOT);
        String host = named.toLowerCase(Locale.ROOT);
        return mine.equals(host) || mine.startsWith(host + "-");
    }

    private static List<Path> descriptors() {
        return filesUnder(repoRoot(), "paper-plugin.yml");
    }

    /** Every file under the build whose name ends in {@code suffix}, with build output left out. */
    private static List<Path> filesUnder(Path root, String suffix) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(suffix))
                    .filter(path -> !path.toString().contains("/build/"))
                    .filter(path -> !path.toString().contains("/.git/"))
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not walk " + root, unreadable);
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
