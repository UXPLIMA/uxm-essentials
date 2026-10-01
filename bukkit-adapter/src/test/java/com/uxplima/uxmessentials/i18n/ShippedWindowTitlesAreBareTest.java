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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The windows this plugin draws itself ship their titles bare, in every language.
 *
 * <p>A window title is centred and bare (docs/14-ui-style), and this used to be kept by stripping every title of its
 * style when the window opened. That also stripped what an operator wrote: a translated key came out as its raw name and
 * a resource-pack font was dropped, so a server drawing its windows with a pack could not. The title is now drawn as
 * written, so the house look is kept where the house writes it: in the shipped catalogues, with no tag at all.
 */
class ShippedWindowTitlesAreBareTest {

    /** The titles of the windows that open a real container rather than going through the menu engine. */
    private static final List<String> WINDOW_TITLES = List.of(
            "vaults.title",
            "vaults.admin.title",
            "kit.editor.gui-title",
            "kit.preview.gui-title",
            "itemworld.shulker.title",
            "itemworld.disposal.title");

    private static final Pattern ENTRY = Pattern.compile("^\\s*\"([^\"]+)\"\\s*=\\s*\"(.*)\"\\s*$");

    @Test
    void everyShippedWindowTitleIsWrittenWithoutATag() {
        List<Path> catalogues = catalogues();
        assertThat(catalogues).as("the shipped catalogues").hasSizeGreaterThanOrEqualTo(2);

        List<String> styled = new ArrayList<>();
        for (Path catalogue : catalogues) {
            List<String> found = new ArrayList<>();
            for (String line : lines(catalogue)) {
                Matcher entry = ENTRY.matcher(line);
                if (entry.matches() && WINDOW_TITLES.contains(entry.group(1))) {
                    found.add(entry.group(1));
                    if (entry.group(2).contains("<")) {
                        styled.add(catalogue.getFileName() + "  " + entry.group(1) + " = " + entry.group(2));
                    }
                }
            }
            assertThat(found)
                    .as("every window title in %s", catalogue.getFileName())
                    .containsExactlyInAnyOrderElementsOf(WINDOW_TITLES);
        }
        assertThat(styled)
                .as("a shipped window title carries a tag:\n%s", String.join("\n", styled))
                .isEmpty();
    }

    private static List<Path> catalogues() {
        Path folder = repoRoot().resolve("bukkit-adapter/src/messages/resources/messages");
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.getFileName().toString().matches("messages_[a-z]+\\.conf"))
                    .sorted()
                    .toList();
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to list " + folder, failure);
        }
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to read " + file, failure);
        }
    }

    /** The repository root, found by walking up from the working directory until the catalogues are in sight. */
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
