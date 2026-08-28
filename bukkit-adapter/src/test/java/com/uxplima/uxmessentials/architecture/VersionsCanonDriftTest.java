package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
 * The version-catalog canon guard (CLAUDE.md §1 + §7, docs/04-build.md §22, docs/05-testing.md §16).
 *
 * <p><strong>The bug this freezes out.</strong> {@code docs/04-build.md} §4 prints a full copy of
 * {@code gradle/libs.versions.toml} and calls it the canonical catalog. That copy is what every new plugin cloned
 * from this template starts life with, so a stale pin there is not a documentation typo: it is the build the next
 * project inherits. Nothing was comparing the two. {@code verify-canon.sh} looked green while the doc still pinned
 * Java 21, Paper 1.21, Adventure 4, MockBukkit's {@code mockbukkit-v1.21} artifact and Jedis, because all it asked
 * was whether those coordinates still exist in a Maven repository. Old releases are never withdrawn, so the answer
 * was always yes and the gate could never fail. The doc drifted a whole Minecraft generation behind the build it
 * claimed to describe, and the guard the docs said was holding it in place had never been written.
 *
 * <p><strong>The invariant.</strong> {@code docs/04-build.md} §4 and {@code gradle/libs.versions.toml} are the same
 * document. Every {@code [versions]} pin, every {@code [libraries]} coordinate, every {@code [bundles]} list and
 * every {@code [plugins]} id must match exactly, in both directions: an entry the build gained must appear in the
 * doc, and an entry the doc still carries must exist in the build. Bumping a dependency therefore means editing
 * both halves in the same commit, which is precisely the discipline CLAUDE.md §7 asks for.
 *
 * <p><strong>Why this lives in a test and not only in the shell script.</strong> {@code verify-canon.sh} answers a
 * different question (does this coordinate resolve at some repository), it needs the network, and nobody runs it on
 * a normal edit. This runs inside {@code ./gradlew check}, offline, on every build.
 *
 * <p><strong>Scope.</strong> {@code docs/} is a working directory that is deliberately kept out of the published
 * repository, so a clone without it skips this test rather than failing it. Where the doc is present, it is held to
 * the letter.
 */
class VersionsCanonDriftTest {

    private static final String DOC = "docs/04-build.md";
    private static final String CATALOG = "gradle/libs.versions.toml";

    /** A {@code key = "value"} pin, ignoring any trailing {@code #} comment. */
    private static final Pattern PIN = Pattern.compile("^([A-Za-z0-9_-]+)\\s*=\\s*\"([^\"]*)\"");

    /** A {@code key = { ... }} table entry: the key, then the braced body to pull fields out of. */
    private static final Pattern TABLE = Pattern.compile("^([A-Za-z0-9_.-]+)\\s*=\\s*\\{(.*)\\}\\s*$");

    /** A {@code key = ["a", "b"]} list entry. */
    private static final Pattern LIST = Pattern.compile("^([A-Za-z0-9_-]+)\\s*=\\s*\\[(.*)]\\s*$");

    private static final Pattern FIELD = Pattern.compile("([A-Za-z.]+)\\s*=\\s*\"([^\"]*)\"");

    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");

    @Test
    void theDocumentedVersionPinsAreTheOnesTheBuildUses() {
        assertSectionMatches("versions");
    }

    @Test
    void theDocumentedLibraryCoordinatesAreTheOnesTheBuildUses() {
        assertSectionMatches("libraries");
    }

    @Test
    void theDocumentedBundlesAreTheOnesTheBuildUses() {
        assertSectionMatches("bundles");
    }

    @Test
    void theDocumentedGradlePluginsAreTheOnesTheBuildUses() {
        assertSectionMatches("plugins");
    }

    @Test
    void theDocumentedCatalogCarriesNoUnresolvedPlaceholder() {
        Map<String, String> documented = parse(documentedCatalog(), "versions");
        List<String> unresolved = new ArrayList<>();
        documented.forEach((key, value) -> {
            if (value.contains("TODO-VERIFY")) {
                unresolved.add(key);
            }
        });
        assertThat(unresolved)
                .as(
                        "%s §4 still carries an unresolved TODO-VERIFY pin. CLAUDE.md §7: a version that cannot be"
                                + " proven does not get into master.",
                        DOC)
                .isEmpty();
    }

    private void assertSectionMatches(String section) {
        Map<String, String> real = parse(realCatalog(), section);
        Map<String, String> documented = parse(documentedCatalog(), section);
        assertThat(documented)
                .as(
                        "%s §4 documents [%s] as the canonical catalog, so it must equal %s entry for entry."
                                + " Bump both halves in the same commit (CLAUDE.md §7).",
                        DOC, section, CATALOG)
                .containsExactlyInAnyOrderEntriesOf(real);
    }

    /**
     * Reduce one catalog section to a comparable {@code key -> canonical form} map. Pins compare by their value,
     * table entries by their fields in a fixed order, bundles by their listed members. Comments, blank lines and
     * whitespace are dropped, so the two copies may be laid out and annotated differently while still being held to
     * the same content.
     */
    private static Map<String, String> parse(List<String> lines, String section) {
        Map<String, String> entries = new LinkedHashMap<>();
        boolean inside = false;
        for (String raw : lines) {
            String line = raw.strip();
            if (line.startsWith("[")) {
                inside = line.equals("[" + section + "]");
                continue;
            }
            if (!inside || line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Matcher table = TABLE.matcher(line);
            if (table.matches()) {
                entries.put(table.group(1), fields(table.group(2)));
                continue;
            }
            Matcher list = LIST.matcher(line);
            if (list.matches()) {
                entries.put(list.group(1), members(list.group(2)));
                continue;
            }
            Matcher pin = PIN.matcher(line);
            if (pin.matches() || pin.lookingAt()) {
                entries.put(pin.group(1), pin.group(2));
            }
        }
        return entries;
    }

    /** The fields of a table entry, rendered in a fixed order so two spellings of the same entry compare equal. */
    private static String fields(String body) {
        Map<String, String> found = new LinkedHashMap<>();
        Matcher matcher = FIELD.matcher(body);
        while (matcher.find()) {
            found.put(matcher.group(1), matcher.group(2));
        }
        StringBuilder rendered = new StringBuilder();
        for (String key : List.of("module", "id", "version", "version.ref")) {
            String value = found.get(key);
            if (value != null) {
                rendered.append(rendered.isEmpty() ? "" : " ")
                        .append(key)
                        .append('=')
                        .append(value);
            }
        }
        return rendered.toString();
    }

    /** The members of a bundle, in declaration order. */
    private static String members(String body) {
        StringBuilder rendered = new StringBuilder();
        Matcher matcher = QUOTED.matcher(body);
        while (matcher.find()) {
            rendered.append(rendered.isEmpty() ? "" : ",").append(matcher.group(1));
        }
        return rendered.toString();
    }

    private static List<String> realCatalog() {
        return readLines(repoRoot().resolve(CATALOG));
    }

    /**
     * The catalog as printed in the doc: the one fenced {@code toml} block inside §4. The doc holds other toml
     * fences, so the block is taken from the section rather than from the file as a whole.
     */
    private static List<String> documentedCatalog() {
        Path doc = repoRoot().resolve(DOC);
        assumeTrue(Files.exists(doc), DOC + " is not present in this checkout (docs/ stays out of the public repo)");
        List<String> lines = readLines(doc);
        List<String> block = new ArrayList<>();
        boolean inSection = false;
        boolean inFence = false;
        for (String line : lines) {
            if (line.startsWith("## ")) {
                if (inSection) {
                    break;
                }
                inSection = line.startsWith("## 4. ");
                continue;
            }
            if (!inSection) {
                continue;
            }
            if (line.startsWith("```")) {
                if (inFence) {
                    break;
                }
                inFence = line.startsWith("```toml");
                continue;
            }
            if (inFence) {
                block.add(line);
            }
        }
        assertThat(block)
                .as("expected the canonical version catalog as a ```toml block inside %s §4", DOC)
                .isNotEmpty();
        return block;
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
