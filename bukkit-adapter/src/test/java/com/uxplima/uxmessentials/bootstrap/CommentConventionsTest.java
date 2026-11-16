package com.uxplima.uxmessentials.bootstrap;

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

import org.junit.jupiter.api.Test;

/**
 * Comments say why, never when: no issue number, no plan phase, no section of a design note, and no "as before".
 * Git and the pull request keep the history. A comment that names one goes stale as soon as the plan moves on, and
 * a reader without the note cannot follow it. The comments of every module's main sources are read for one.
 *
 * <p>A section sign is allowed after an RFC, since the standard is what the reader looks up.
 */
class CommentConventionsTest {

    /** One way a comment points at history instead of explaining the code. */
    private enum Rule {
        ISSUE_NUMBER("(?<![\\w&{$#])#\\d+(?!\\w)"),
        PHASE("\\bphase[ -]?\\d|\\bP\\d{1,2}(?:-T\\d+)?\\b|\\blater (?:sub-)?phases?\\b"),
        SECTION("§\\s*\\d"),
        HISTORY("\\bas before\\b|\\buntil now\\b");

        private final Pattern pattern;

        Rule(String regex) {
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        }
    }

    @Test
    void noMainSourceCommentPointsAtHistory() throws IOException {
        Path root = repositoryRoot();
        List<String> found = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                Path sources = module.resolve("src/main/java");
                if (!Files.isDirectory(sources)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(sources)) {
                    for (Path file :
                            files.filter(f -> f.toString().endsWith(".java")).toList()) {
                        scanned++;
                        String name = root.relativize(file).toString().replace('\\', '/');
                        found.addAll(historyIn(name, read(file)));
                    }
                }
            }
        }

        assertThat(scanned).as("the scan read the main sources").isGreaterThan(1000);
        assertThat(found)
                .as("comments that name an issue, a plan phase, a design-note section or what the code did before;"
                        + " say why the code is the way it is instead")
                .isEmpty();
    }

    @Test
    void everyKindOfHistoryIsCaught() {
        String code = """
                class Sample {
                    // Fixed in #95, the leak is gone.
                    /** Phase 2 adds the money row. */
                    // The P4 access gate reads this (P4-T3).
                    // The guarded path in §3 is never debounced.
                    // The window opens exactly as before.
                    // Which is what every server has seen until now.
                    // The rest lands in the later phases.
                }
                """;

        assertThat(historyIn("sample", code))
                .containsExactly(
                        "sample:2 ISSUE_NUMBER #95",
                        "sample:3 PHASE Phase 2",
                        "sample:4 PHASE P4",
                        "sample:4 PHASE P4-T3",
                        "sample:5 SECTION §3",
                        "sample:6 HISTORY as before",
                        "sample:7 HISTORY until now",
                        "sample:8 PHASE later phases");
    }

    @Test
    void codeColoursRfcSectionsAndStringsDoNotCount() {
        String code = """
                class Sample {
                    // A colour code such as {@code #77ff77} or {@code &a}, a format such as {@code $#,##0.00}.
                    // The step is checked as RFC 6238 §5.2 asks.
                    String tag = "#95 as before";
                    /** The legacy {@code §c} colour sign and the {@code §Economy} section of a page. */
                }
                """;

        assertThat(historyIn("sample", code)).isEmpty();
    }

    private static List<String> historyIn(String name, String source) {
        List<String> found = new ArrayList<>();
        for (Comment comment : comments(source)) {
            for (Rule rule : Rule.values()) {
                Matcher matcher = rule.pattern.matcher(comment.text());
                while (matcher.find()) {
                    if (rule == Rule.SECTION
                            && comment.text().toUpperCase(Locale.ROOT).contains("RFC")) {
                        continue;
                    }
                    found.add(name + ":" + comment.line() + " " + rule + " " + matcher.group());
                }
            }
        }
        return found;
    }

    /** One line of a comment, with the line it sits on. */
    private record Comment(int line, String text) {}

    /** Every comment line of {@code source}, skipping strings, text blocks and char literals. */
    private static List<Comment> comments(String source) {
        List<Comment> comments = new ArrayList<>();
        int i = 0;
        int n = source.length();
        int line = 1;
        while (i < n) {
            char c = source.charAt(i);
            if (c == '\n') {
                line++;
                i++;
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                int end = source.indexOf('\n', i);
                end = end < 0 ? n : end;
                comments.add(new Comment(line, source.substring(i, end)));
                i = end;
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                String[] lines = source.substring(i, end).split("\n", -1);
                for (int k = 0; k < lines.length; k++) {
                    comments.add(new Comment(line + k, lines[k]));
                }
                line += lines.length - 1;
                i = end;
            } else if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                end = end < 0 ? n : end + 3;
                line += (int) source.substring(i, end)
                        .chars()
                        .filter(ch -> ch == '\n')
                        .count();
                i = end;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && source.charAt(j) != c && source.charAt(j) != '\n') {
                    j += source.charAt(j) == '\\' ? 2 : 1;
                }
                i = Math.min(n, j + 1);
            } else {
                i++;
            }
        }
        return comments;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static Path repositoryRoot() {
        String root = System.getProperty("uxm.repositoryRoot");
        assertThat(root)
                .as("the build passes uxm.repositoryRoot to the test JVM")
                .isNotNull();
        return Path.of(root).toAbsolutePath().normalize();
    }
}
