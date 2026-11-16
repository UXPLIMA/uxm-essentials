package com.uxplima.uxmessentials.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Two code-style rules the compiler does not check, read from the main sources of every module.
 *
 * <p>Every package is null-marked through its {@code package-info.java}. Without one, NullAway and the reader both
 * lose the default, and a package added later quietly starts without it.
 *
 * <p>A caught exception is named for what it is. A one-letter name says nothing in a log call or a rethrow, and a
 * reader has to scroll up to the catch to learn what failed.
 */
class SourceConventionsTest {

    private static final Pattern NULL_MARKED =
            Pattern.compile("^@(?:org\\.jspecify\\.annotations\\.)?NullMarked\\b", Pattern.MULTILINE);

    private static final Pattern CATCH =
            Pattern.compile("\\bcatch\\s*\\(\\s*(?:final\\s+)?[\\w.|\\s]+?\\s+(\\w+)\\s*\\)");

    /** Names that say only that something was caught, not what. */
    private static final Set<String> GENERIC = Set.of("ex", "exc", "err", "exception", "throwable");

    @Test
    void everyMainPackageIsNullMarkedInItsPackageInfo() throws IOException {
        Path root = repositoryRoot();
        Set<String> unmarked = new TreeSet<>();
        int packages = 0;
        for (Path sources : mainSourceRoots(root)) {
            try (Stream<Path> files = Files.walk(sources)) {
                Set<Path> directories = new TreeSet<>();
                files.filter(f -> f.toString().endsWith(".java")).forEach(f -> directories.add(f.getParent()));
                for (Path directory : directories) {
                    packages++;
                    if (!nullMarked(directory.resolve("package-info.java"))) {
                        unmarked.add(slashed(root.relativize(directory)));
                    }
                }
            }
        }

        assertThat(packages).as("the scan read the main packages").isGreaterThan(400);
        assertThat(unmarked)
                .as("packages without a package-info.java carrying @NullMarked")
                .isEmpty();
    }

    @Test
    void everyCaughtExceptionIsNamedForWhatItIs() throws IOException {
        Path root = repositoryRoot();
        List<String> generic = new ArrayList<>();
        int scanned = 0;
        for (Path sources : mainSourceRoots(root)) {
            try (Stream<Path> files = Files.walk(sources)) {
                for (Path file :
                        files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    scanned++;
                    generic.addAll(genericCatches(slashed(root.relativize(file)), read(file)));
                }
            }
        }

        assertThat(scanned).as("the scan read the main sources").isGreaterThan(1000);
        assertThat(generic)
                .as("caught exceptions named e, t or ex; name them for what failed (notANumber, unreadable, failure)")
                .isEmpty();
    }

    @Test
    void aGenericCatchNameIsCaughtAndADescriptiveOneIsNot() {
        String code = """
                class Sample {
                    void run() {
                        try { work(); } catch (IOException e) { log(e); }
                        try { work(); } catch (IllegalStateException | IllegalArgumentException ex) { log(ex); }
                        try { work(); } catch (final Throwable t) { log(t); }
                        try { work(); } catch (NumberFormatException notANumber) { log(notANumber); }
                        // catch (Exception e) in a comment does not count
                        String text = "catch (Exception e)";
                    }
                }
                """;

        assertThat(genericCatches("sample", code)).containsExactly("sample:3 e", "sample:4 ex", "sample:5 t");
    }

    @Test
    void aPackageInfoWithoutTheAnnotationDoesNotCount() {
        assertThat(NULL_MARKED.matcher("/** Docs. */\npackage com.example;\n").find())
                .isFalse();
        assertThat(NULL_MARKED
                        .matcher("@org.jspecify.annotations.NullMarked\npackage com.example;\n")
                        .find())
                .isTrue();
        assertThat(NULL_MARKED
                        .matcher("@NullMarked\npackage com.example;\n\nimport org.jspecify.annotations.NullMarked;\n")
                        .find())
                .isTrue();
    }

    private static List<String> genericCatches(String name, String source) {
        String code = codeOnly(source);
        List<String> found = new ArrayList<>();
        Matcher matcher = CATCH.matcher(code);
        while (matcher.find()) {
            String variable = matcher.group(1);
            if (variable.length() == 1 || GENERIC.contains(variable)) {
                long line = code.substring(0, matcher.start())
                                .chars()
                                .filter(c -> c == '\n')
                                .count()
                        + 1;
                found.add(name + ":" + line + " " + variable);
            }
        }
        return found;
    }

    private static boolean nullMarked(Path packageInfo) {
        return Files.isRegularFile(packageInfo)
                && NULL_MARKED.matcher(read(packageInfo)).find();
    }

    private static List<Path> mainSourceRoots(Path root) throws IOException {
        try (Stream<Path> modules = Files.list(root)) {
            return modules.map(module -> module.resolve("src/main/java"))
                    .filter(Files::isDirectory)
                    .sorted()
                    .toList();
        }
    }

    /**
     * The source with every comment and every string, text block and char literal blanked out, newlines kept so the
     * line numbers stay right. A comment or a string may quote a catch clause without being one.
     */
    private static String codeOnly(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                i = blank(source, i, end < 0 ? n : end + 2, out);
            } else if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                while (end > 0 && source.charAt(end - 1) == '\\') {
                    end = source.indexOf("\"\"\"", end + 1);
                }
                i = blank(source, i, end < 0 ? n : end + 3, out);
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && source.charAt(j) != c && source.charAt(j) != '\n') {
                    j += source.charAt(j) == '\\' ? 2 : 1;
                }
                i = blank(source, i, Math.min(n, j + 1), out);
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int blank(String source, int from, int to, StringBuilder out) {
        for (int k = from; k < to; k++) {
            out.append(source.charAt(k) == '\n' ? '\n' : ' ');
        }
        return to;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static String slashed(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static Path repositoryRoot() {
        String root = System.getProperty("uxm.repositoryRoot");
        assertThat(root)
                .as("the build passes uxm.repositoryRoot to the test JVM")
                .isNotNull();
        return Path.of(root).toAbsolutePath().normalize();
    }
}
