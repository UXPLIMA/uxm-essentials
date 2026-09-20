package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A javadoc comment that sends the reader to a document sends them to one that is there.
 *
 * <p>A reference into {@code docs/} is the cheapest documentation this estate has: it costs a line and it
 * puts the reasoning one click from the code that needs it. It is also the easiest to break, because
 * renaming a document breaks nothing that compiles, and a reader who follows two dead links stops
 * following them. uxmEssentials had eleven of its fourteen documents missing under 401 such references
 * before they were written, and two plugins here pointed at a document of their own that does not exist
 * and never did: they meant the canon in another repository and did not say so.
 *
 * <p>Which is exactly the distinction this reads. A bare {@code docs/...} is this repository's own
 * document and has to be here. A reference carrying a repository in front of it,
 * {@code uxm-setups/docs/13-bedrock.md}, belongs to another checkout and is left alone: this guard cannot
 * see that tree and must not pretend to.
 */
final class EveryCitedDocumentExistsTest {

    /**
     * A reference to a document of ours.
     *
     * <p>The lookbehind is the whole point: it refuses a match whose {@code docs/} is preceded by a word
     * character, a hyphen or a slash, which is what a reference into another repository always looks like.
     */
    private static final Pattern OUR_DOCUMENT = Pattern.compile("(?<![\\w/-])docs/([\\w./-]+\\.md)");

    /** This test's own source, which holds an example of each shape and is therefore not scanned. */
    private static final String OWN_FILE = EveryCitedDocumentExistsTest.class.getSimpleName() + ".java";

    /**
     * The repository root, which is not the working directory here.
     *
     * <p>This plugin is ten modules and the tests run from one of them, so a reference is resolved
     * against the root and every module's Java is read, the guards in {@code src/test} included: they
     * cite documents more often than the production code does.
     */
    private static final Path ROOT = ProductionSources.repoRoot();

    @Test
    @DisplayName("every document this repository's own code points at exists")
    void everyCitedDocumentIsThere() throws IOException {
        List<String> dead = new ArrayList<>();
        for (Path source : sources()) {
            Matcher found = OUR_DOCUMENT.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (found.find()) {
                Path cited = ROOT.resolve("docs").resolve(found.group(1));
                if (!Files.isRegularFile(cited)) {
                    dead.add(source + " points at " + cited);
                }
            }
        }

        assertThat(dead)
                .describedAs("a reference into docs/ that goes nowhere teaches a reader to stop following"
                        + " them. Write the document, fix the name, or name the repository it is in")
                .isEmpty();
    }

    /**
     * The scan itself, against text rather than against the tree.
     *
     * <p>Six plugins of this family cite no document of their own at all, so the test above passes there
     * whatever the pattern does. This is what keeps it from being a guard that cannot fail: it pins both
     * halves of the rule, the reference that must be checked and the one that must not.
     */
    @Test
    @DisplayName("a bare reference is ours and a prefixed one is another repository's")
    void theScanKnowsWhichReferenceIsOurs() {
        assertThat(citedIn("See {@code docs/01-architecture.md} for the layers."))
                .containsExactly("01-architecture.md");
        assertThat(citedIn("See docs/adr/0004-every-menu-is-a-file.md."))
                .containsExactly("adr/0004-every-menu-is-a-file.md");
        assertThat(citedIn("One definition, two clients. See uxm-setups/docs/13-bedrock.md."))
                .isEmpty();
        assertThat(citedIn("see uxmsetups docs/11-placeholders.md")).containsExactly("11-placeholders.md");
    }

    private static List<String> citedIn(String text) {
        List<String> found = new ArrayList<>();
        Matcher match = OUR_DOCUMENT.matcher(text);
        while (match.find()) {
            found.add(match.group(1));
        }
        return found;
    }

    private static List<Path> sources() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> modules = Files.list(ROOT)) {
            List<Path> roots = new ArrayList<>();
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                roots.add(module.resolve("src/main/java"));
                roots.add(module.resolve("src/test/java"));
            }
            addJava(roots, files);
        }
        return files;
    }

    private static void addJava(List<Path> roots, List<Path> files) throws IOException {
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> tree = Files.walk(root)) {
                tree.filter(path -> path.toString().endsWith(".java"))
                        // This file holds both shapes as fixtures, so scanning it would report its own
                        // examples as dead references. The fixtures are asserted directly instead.
                        .filter(path -> !path.endsWith(OWN_FILE))
                        .forEach(files::add);
            }
        }
    }
}
