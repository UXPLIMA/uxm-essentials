package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The version-drift guard: the document that ships and the build that ships name the same server and the same
 * Java.
 *
 * <p><strong>What this replaces, and why.</strong> This guard used to compare a fenced copy of
 * {@code gradle/libs.versions.toml} printed inside {@code docs/04-build.md} against the real catalog.
 * {@code docs/} is a working directory that has never been in this repository, so in every checkout the guard
 * called {@code Assumptions.assumeTrue} and aborted. JUnit records an abort as a skip, the suite reported
 * green, and five tests guarded nothing for as long as they existed. CONTRACT.md section 15 says there is no
 * legitimate skip here: a test that cannot run needs replacing, not excusing.
 *
 * <p><strong>The invariant now.</strong> {@code README.md} is the document that does ship, and it is the one
 * an operator reads before downloading anything. It names the server, the server build and the Java a person
 * must be running. Each of those is a pin in the catalog, and the Java one is also a constant in the
 * convention plugin. Every file involved is in this repository, so the whole check runs offline on every
 * build, which is what the old one could never do.
 *
 * <p><strong>The trade.</strong> The catalog is no longer held entry for entry against a printed copy,
 * because the printed copy is not here to hold it against. What is held is the part a reader acts on: the
 * numbers that decide whether the jar runs at all on the server they have. A pin that no document mentions is
 * now unguarded, and that is the price of the doc leaving the repository.
 */
class ShippedVersionsDriftTest {

    private static final String README = "README.md";
    private static final String CATALOG = "gradle/libs.versions.toml";
    private static final String CONVENTIONS = "buildSrc/src/main/kotlin/uxmessentials.java-conventions.gradle.kts";

    /** A {@code key = "value"} pin, ignoring any trailing {@code #} comment. */
    private static final Pattern PIN = Pattern.compile("^([A-Za-z0-9_-]+)\\s*=\\s*\"([^\"]*)\"");

    /** The requirements row naming the server: {@code | Server | Paper **26.2** (build 112), ... |}. */
    private static final Pattern README_SERVER =
            Pattern.compile("\\|\\s*Server\\s*\\|\\s*Paper\\s*\\*\\*([0-9][0-9.]*)\\*\\*\\s*\\(build\\s*([0-9]+)\\)");

    /** The requirements row naming the runtime: {@code | Java | **25** |}. */
    private static final Pattern README_JAVA =
            Pattern.compile("\\|\\s*Java\\s*\\|\\s*\\*\\*([0-9][0-9.]*)\\*\\*\\s*\\|");

    /** The catalog's Paper pin, {@code <version>.build.<build>-<channel>}. */
    private static final Pattern PAPER_PIN = Pattern.compile("^([0-9][0-9.]*)\\.build\\.([0-9]+)");

    private static final Pattern TOOLCHAIN = Pattern.compile("JavaLanguageVersion\\.of\\(([0-9]+)\\)");

    private static final Pattern RELEASE = Pattern.compile("options\\.release\\s*=\\s*([0-9]+)");

    @Test
    void theReadmeNamesTheServerTheBuildCompilesAgainst() {
        assertThat(readmeServer()[0])
                .as(
                        "%s tells an operator which Paper to run and %s is what the code is compiled against."
                                + " Bump both halves in the same commit: a reader who installs the version the"
                                + " README names and gets a NoSuchMethodError has been misled by us.",
                        README, CATALOG)
                .isEqualTo(paperPin()[0]);
    }

    @Test
    void theReadmeNamesTheServerBuildTheBuildCompilesAgainst() {
        assertThat(readmeServer()[1])
                .as(
                        "%s names a Paper build number and %s pins one. The build is where a mid-version API"
                                + " change lands, so naming the wrong one sends an operator to a server that"
                                + " compiles here and fails there.",
                        README, CATALOG)
                .isEqualTo(paperPin()[1]);
    }

    @Test
    void theReadmeNamesTheJavaTheBuildTargets() {
        assertThat(readmeJava())
                .as(
                        "%s tells an operator which Java to run and %s pins the one the build targets. A server"
                                + " on an older Java does not start the plugin, and the error it prints names a"
                                + " class file version rather than a Java release.",
                        README, CATALOG)
                .isEqualTo(javaPin());
    }

    /**
     * The release number is not a requirement, so it is not in the table above and it is not in the prose
     * either. This file named {@code uxmEssentials-0.8.2.jar} in eleven places while the build fell back to
     * 0.9.1, which is the same defect the uxmLib version in twenty one {@code CLAUDE.md} files was: a number
     * a person types into a sentence is stale the day after they type it, and nothing tells them.
     *
     * <p>The jar names read {@code uxmEssentials-<version>.jar} now, and the versioning section points at the
     * releases page. This holds that: a jar name in the README may not carry a number.
     */
    @Test
    void theReadmeNamesNoReleaseNumber() {
        Pattern numbered = Pattern.compile("uxmEssentials(?:-[a-z]+)?-([0-9]+\\.[0-9]+\\.[0-9]+)\\.jar");
        Matcher found = numbered.matcher(read(repoRoot().resolve(README)));

        assertThat(found.find())
                .as(
                        "%s must not print a release number: it named one in eleven places while the build"
                                + " had moved past it. Write uxmEssentials-<version>.jar instead.",
                        README)
                .isFalse();
    }

    @Test
    void theConventionPluginCompilesAgainstThePinnedJava() {
        String pinned = javaPin();
        assertThat(List.of(matchIn(CONVENTIONS, TOOLCHAIN), matchIn(CONVENTIONS, RELEASE)))
                .as(
                        "the toolchain and the release level in %s are the Java the catalog pins. Three places"
                                + " say the Java version and only one of them is read by a person, so they are"
                                + " bumped together or the README ends up describing neither.",
                        CONVENTIONS)
                .containsExactly(pinned, pinned);
    }

    @Test
    void theCatalogCarriesNoUnresolvedPlaceholder() {
        Map<String, String> pins = versions();
        List<String> unresolved = new ArrayList<>();
        pins.forEach((key, value) -> {
            if (value.contains("TODO-VERIFY")) {
                unresolved.add(key);
            }
        });
        assertThat(unresolved)
                .as(
                        "%s still carries an unresolved TODO-VERIFY pin. A version that cannot be proven does not"
                                + " get into master.",
                        CATALOG)
                .isEmpty();
    }

    /** The server version and the server build the README's requirements table names. */
    private static String[] readmeServer() {
        Matcher matcher = README_SERVER.matcher(read(repoRoot().resolve(README)));
        assertThat(matcher.find())
                .as("expected a requirements row naming the Paper version and build in %s", README)
                .isTrue();
        return new String[] {matcher.group(1), matcher.group(2)};
    }

    /** The Java version the README's requirements table names. */
    private static String readmeJava() {
        Matcher matcher = README_JAVA.matcher(read(repoRoot().resolve(README)));
        assertThat(matcher.find())
                .as("expected a requirements row naming the Java version in %s", README)
                .isTrue();
        return matcher.group(1);
    }

    /** The catalog's Paper pin, split into its version and its build. */
    private static String[] paperPin() {
        String pin = pin("paper");
        Matcher matcher = PAPER_PIN.matcher(pin);
        assertThat(matcher.find())
                .as("expected the paper pin in %s to read <version>.build.<build>, and it reads %s", CATALOG, pin)
                .isTrue();
        return new String[] {matcher.group(1), matcher.group(2)};
    }

    private static String javaPin() {
        return pin("java");
    }

    private static String pin(String key) {
        String value = versions().get(key);
        assertThat(value)
                .as("expected a [versions] pin named %s in %s", key, CATALOG)
                .isNotNull();
        return value;
    }

    /** The {@code [versions]} section of the real catalog, as {@code key -> pin}. */
    private static Map<String, String> versions() {
        Map<String, String> pins = new LinkedHashMap<>();
        boolean inside = false;
        for (String raw : readLines(repoRoot().resolve(CATALOG))) {
            String line = raw.strip();
            if (line.startsWith("[")) {
                inside = line.equals("[versions]");
                continue;
            }
            if (!inside || line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Matcher pin = PIN.matcher(line);
            if (pin.lookingAt()) {
                pins.put(pin.group(1), pin.group(2));
            }
        }
        assertThat(pins).as("expected the [versions] section of %s", CATALOG).isNotEmpty();
        return pins;
    }

    /** The first capture of {@code pattern} in the file at {@code relative}. */
    private static String matchIn(String relative, Pattern pattern) {
        Matcher matcher = pattern.matcher(read(repoRoot().resolve(relative)));
        assertThat(matcher.find())
                .as("expected %s to match %s", relative, pattern.pattern())
                .isTrue();
        return matcher.group(1);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + path, e);
        }
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + path, e);
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
