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
 * A line of the catalogue is rendered with the palette attached.
 *
 * <p>This plugin writes MiniMessage in two vocabularies and they are not interchangeable. **The
 * catalogue writes palette tokens**, `&lt;body&gt;` and `&lt;value&gt;` and the rest, which only exist
 * because `StyleTags.resolver()` supplies them. **Operator content writes vanilla colours**,
 * `&lt;yellow&gt;` and `&lt;gray&gt;`, which MiniMessage knows on its own: that is what the shipped
 * join, quit and death templates use, and parsing those bare is correct.
 *
 * <p>So the rule is not "always attach the resolver". It is this: **if the string came from
 * {@code Messages.resolve}, it came from the catalogue, and the catalogue speaks palette tokens.**
 * Deserialising one without the resolver renders `&lt;body&gt;` to the player as the literal five
 * characters, on that one line, in every language.
 *
 * <p>Nothing held that rule but the twelve call sites that remember it. A thirteenth would not have
 * failed anything: the line renders, the colour is simply absent and the tag is simply visible, which is
 * the kind of defect a reviewer reads past and a screenshot shows.
 *
 * <p>The scan walks the balanced parentheses of each {@code deserialize(...)} rather than matching a
 * line, because the call is usually split across three.
 */
final class EveryCatalogueRenderCarriesThePaletteDriftTest {

    private static final Path SOURCE = Path.of("src", "main", "java");

    /** What marks the argument as having come out of the catalogue. */
    private static final String FROM_CATALOGUE = ".resolve(";

    /** What supplies the palette tokens the catalogue writes. */
    private static final String THE_PALETTE = "StyleTags.resolver()";

    @Test
    @DisplayName("every deserialize of a catalogue line attaches the palette resolver")
    void everycatalogueRenderCarriesThePalette() throws IOException {
        List<String> bare = new ArrayList<>();
        for (Path file : java()) {
            String body = Files.readString(file, StandardCharsets.UTF_8);
            for (int[] span : deserializeArguments(body)) {
                String argument = body.substring(span[0], span[1]);
                if (argument.contains(FROM_CATALOGUE) && !argument.contains(THE_PALETTE)) {
                    bare.add(file.getFileName() + ": " + oneLine(argument));
                }
            }
        }

        assertThat(bare)
                .describedAs("a catalogue line rendered without the palette shows the player the literal"
                        + " token, on that one line, in every language")
                .isEmpty();
    }

    @Test
    @DisplayName("the scan finds the catalogue renders, so it cannot pass by reading nothing")
    void thescanFindsTheRenders() throws IOException {
        int found = 0;
        for (Path file : java()) {
            String body = Files.readString(file, StandardCharsets.UTF_8);
            for (int[] span : deserializeArguments(body)) {
                if (body.substring(span[0], span[1]).contains(FROM_CATALOGUE)) {
                    found++;
                }
            }
        }

        assertThat(found)
                .describedAs("this plugin renders catalogue lines, so a scan that finds none is broken")
                .isGreaterThan(3);
    }

    /** The start and end of the argument list of every {@code deserialize(...)} in {@code body}. */
    private static List<int[]> deserializeArguments(String body) {
        List<int[]> spans = new ArrayList<>();
        int at = 0;
        while (true) {
            at = body.indexOf("deserialize(", at);
            if (at < 0) {
                return spans;
            }
            int start = at + "deserialize(".length();
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
