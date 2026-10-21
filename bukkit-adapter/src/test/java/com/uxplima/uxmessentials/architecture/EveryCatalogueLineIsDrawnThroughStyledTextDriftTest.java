package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A line of the catalogue is drawn through {@code StyledText.render}, never parsed on its own.
 *
 * <p>Until 2026-10-21 the rule was that a catalogue line was parsed with {@code StyleTags.resolver()} attached,
 * because the catalogue writes palette tokens, {@code <body>} and {@code <value>}, and parsed bare they reached the
 * player as the literal five characters, on that one line, in every language. The catalogue is styled when its
 * language is read now, so a bare parse no longer shows the token; it still drops this plugin's own tags, the two
 * staff channels, and it is a second way to draw a line that the next change to the first will forget.
 *
 * <p>So the rule is stricter than before: <strong>a string that came from {@code Messages.resolve} goes to
 * {@code StyledText.render}</strong>, and a {@code deserialize(...)} with one in its arguments is a defect whatever it
 * carries. Operator content a plugin parses on its own, a join template with vanilla colours, is not this rule's.
 *
 * <p>The scan walks the balanced parentheses of each call rather than matching a line, because the call is usually
 * split across three.
 */
final class EveryCatalogueLineIsDrawnThroughStyledTextDriftTest {

    private static final Path SOURCE = Path.of("src", "main", "java");

    /** What marks the argument as having come out of the catalogue. */
    private static final String FROM_CATALOGUE = ".resolve(";

    @Test
    @DisplayName("no catalogue line is parsed on its own")
    void noCatalogueLineIsParsedOnItsOwn() throws IOException {
        List<String> bare = new ArrayList<>();
        for (Path file : java()) {
            String body = Files.readString(file, StandardCharsets.UTF_8);
            for (int[] span : arguments(body, "deserialize(")) {
                String argument = body.substring(span[0], span[1]);
                if (argument.contains(FROM_CATALOGUE)) {
                    bare.add(file.getFileName() + ": " + oneLine(argument));
                }
            }
        }

        assertThat(bare)
                .describedAs("a catalogue line parsed on its own drops the plugin's tags and is a second way to"
                        + " draw a line; send it to StyledText.render")
                .isEmpty();
    }

    @Test
    @DisplayName("the scan finds the catalogue lines drawn through StyledText, so it cannot pass by reading nothing")
    void theScanFindsTheRenders() throws IOException {
        int found = 0;
        for (Path file : java()) {
            String body = Files.readString(file, StandardCharsets.UTF_8);
            for (int[] span : arguments(body, "StyledText.render(")) {
                if (body.substring(span[0], span[1]).contains(FROM_CATALOGUE)) {
                    found++;
                }
            }
        }

        assertThat(found)
                .describedAs("this plugin draws catalogue lines, so a scan that finds none is broken")
                .isGreaterThan(3);
    }

    /** The start and end of the argument list of every {@code call} in {@code body}. */
    private static List<int[]> arguments(String body, String call) {
        List<int[]> spans = new ArrayList<>();
        int at = 0;
        while (true) {
            at = body.indexOf(call, at);
            if (at < 0) {
                return spans;
            }
            int start = at + call.length();
            int depth = 1;
            int end = start;
            while (end < body.length() && depth > 0) {
                char here = body.charAt(end);
                if (here == '(') {
                    depth++;
                } else if (here == ')') {
                    depth--;
                }
                end++;
            }
            spans.add(new int[] {start, Math.max(start, end - 1)});
            at = end;
        }
    }

    private static String oneLine(String argument) {
        String flat = String.join(" ", argument.split("\\s+")).strip();
        return flat.length() > 90 ? flat.substring(0, 90) : flat;
    }

    private static List<Path> java() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }
}
