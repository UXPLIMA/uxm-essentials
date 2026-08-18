package com.uxplima.uxmessentials.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The description-width guard (docs/14-ui-style §5).
 *
 * <p>A tooltip will draw a line of any length, so nothing stops a description from running the width of the
 * screen; it just stops reading as a label and starts reading as a paragraph. The canon therefore breaks a
 * description into lines of about thirty-four characters, balanced across them, which is what
 * {@code tools/style/lore.py} does when a block is generated. This guard is the other half: a line hand-edited
 * into the shipped catalog is held to the same width.
 *
 * <p>Only the description section is measured. An information row carries a placeholder whose value is unknown
 * here, and an action line is one short phrase by construction.
 */
class LoreDescriptionWidthDriftTest {

    /** The widest a description line may be, in characters: the canon's wrap width plus a word's worth of slack. */
    private static final int MAX_WIDTH = 38;

    private static final Pattern ENTRY = Pattern.compile("^\\s*\"([^\"]+)\"\\s*=\\s*\"(.*)\"\\s*$");

    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private static final Pattern NEWLINE = Pattern.compile("<newline>");

    private static final String DESCRIPTION_HEADER = "ᴅᴇꜱᴄʀɪᴘᴛɪᴏɴ";

    private static final String INFORMATION_HEADER = "ɪɴꜰᴏʀᴍᴀᴛɪᴏɴ";

    @Test
    void everyDescriptionLineIsNarrowEnoughToReadAsALabel() {
        List<String> violations = new ArrayList<>();
        for (String line : catalog()) {
            Matcher entry = ENTRY.matcher(line);
            if (entry.matches()) {
                measure(entry.group(1), entry.group(2), violations);
            }
        }
        assertThat(violations)
                .as(
                        "a description line wraps at %d characters (docs/14-ui-style §5); "
                                + "regenerate the block with tools/style/modules/<module>.py:\n%s",
                        MAX_WIDTH, String.join("\n", violations))
                .isEmpty();
    }

    /** Collect every over-long line of {@code value}'s description section, if it has one. */
    private static void measure(String key, String value, List<String> violations) {
        boolean inDescription = false;
        for (String segment : NEWLINE.split(value, -1)) {
            String text = TAG.matcher(segment).replaceAll("").strip();
            if (text.contains(DESCRIPTION_HEADER)) {
                inDescription = true;
                continue;
            }
            if (!inDescription) {
                continue;
            }
            if (text.isEmpty() || text.contains(INFORMATION_HEADER) || startsWithIcon(text)) {
                inDescription = false;
                continue;
            }
            if (text.length() > MAX_WIDTH) {
                violations.add(key + "  (" + text.length() + " chars)  " + text);
            }
        }
    }

    /** Whether {@code text} opens with one of the icons that ends the description section. */
    private static boolean startsWithIcon(String text) {
        return text.startsWith("→") || text.startsWith("•") || text.startsWith("≡") || text.startsWith("✎");
    }

    private static List<String> catalog() {
        Path path = repoRoot().resolve("bukkit-adapter/src/messages/resources/messages/messages_en.conf");
        assertThat(path).as("the shipped English catalog").exists();
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to read " + path, failure);
        }
    }

    /** The repository root, found by walking up from the working directory until the catalog is in sight. */
    private static Path repoRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.isDirectory(candidate.resolve("bukkit-adapter/src/messages"))) {
            candidate = candidate.getParent();
        }
        assertThat(candidate)
                .as("a directory holding bukkit-adapter/src/messages")
                .isNotNull();
        return candidate;
    }
}
