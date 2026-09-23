package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every escape of text a sender typed knows the tags the text is parsed with.
 *
 * <p>This plugin parses its lines with MiniMessage's tags and the theme's: every colour role, and the badges
 * {@code <tag:'...'>} and {@code <etag:'...'>}. {@code MiniMessage.escapeTags(text)} with no resolver escapes only
 * MiniMessage's own, so a player without the colour node could write {@code <tag:'STAFF'>} into a private message,
 * a vault name or a spied command and the reader saw a staff badge. The usage line lost an argument called
 * {@code <value>} the same way. An escape names {@code StyleTags.resolver()}, or it is on the list below with the
 * reason its text is never parsed with the theme.
 */
class EveryEscapeKnowsTheThemeTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /** An escape with one argument: nothing after the text but the closing bracket. */
    private static final Pattern BARE_ESCAPE = Pattern.compile("escapeTags\\(([^(),]|\\([^()]*\\))+\\)");

    /** Files whose escaped text is parsed with MiniMessage's tags alone. */
    private static final Set<String> PARSED_WITHOUT_THE_THEME = Set.of(
            // The backup notice is parsed by a bare MiniMessage instance with its own palette string in front.
            "BackupCommand.java");

    @Test
    @DisplayName("every escape names the theme's tags, so a typed badge arrives as the characters typed")
    void everyEscapeKnowsTheTheme() throws IOException {
        List<String> bare = new ArrayList<>();
        for (Path file : sources()) {
            if (PARSED_WITHOUT_THE_THEME.contains(file.getFileName().toString())) {
                continue;
            }
            Matcher escape = BARE_ESCAPE.matcher(Files.readString(file));
            while (escape.find()) {
                bare.add(file.getFileName() + ": " + escape.group());
            }
        }

        assertThat(bare)
                .describedAs("these escape MiniMessage's tags and leave the theme's, which the line is parsed with")
                .isEmpty();
    }

    @Test
    @DisplayName("the check reads real code, so it cannot pass by reading nothing")
    void theCheckReadsCode() throws IOException {
        assertThat(sources()).hasSizeGreaterThan(100);
        assertThat(BARE_ESCAPE.matcher("MINI_MESSAGE.escapeTags(text)").find()).isTrue();
        assertThat(BARE_ESCAPE
                        .matcher("MINI_MESSAGE.escapeTags(text, StyleTags.resolver())")
                        .find())
                .isFalse();
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
