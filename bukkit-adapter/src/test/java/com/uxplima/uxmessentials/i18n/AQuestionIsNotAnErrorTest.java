package com.uxplima.uxmessentials.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The error prefix is for a refusal. It ignores the category it is given and prints a red ERROR, so a line
 * that asks the reader to confirm something, or tells them the next step, must not wear it.
 *
 * <p>On 2026-10-20 {@code /worlds delete} asked "permanently delete the world?" under a red ERROR, and an
 * operator reading the console took the question for a failure. Eight more lines did the same.
 */
class AQuestionIsNotAnErrorTest {

    private static final Pattern ENTRY = Pattern.compile("^\\s*\"([^\"]+)\"\\s*=\\s*\"(.*)\"\\s*$");

    /** A click that runs a confirming command is an offer to go on, never a refusal. */
    private static final Pattern CONFIRMING_CLICK = Pattern.compile("<click:run_command:'[^']*confirm[^']*'>");

    /** The lines that were written as refusals and are not. */
    private static final Set<String> NOT_REFUSALS = Set.of(
            "eco.admin.resetall-confirm",
            "pwarp.admin.purge-confirm",
            "world.delete-confirm",
            "world.restore.confirm",
            "security.pin.create-prompt",
            "security.pin.create-confirm",
            "moderation.broadcast.warn",
            "moderation.warn.notify-target",
            "skin.info.none");

    @Test
    void noLineOfferingToConfirmOpensWithTheErrorPrefix() {
        List<String> wrong = new ArrayList<>();
        for (Path catalog : catalogs()) {
            for (String line : read(catalog)) {
                Matcher entry = ENTRY.matcher(line);
                if (!entry.matches() || !entry.group(2).startsWith("<etag:")) {
                    continue;
                }
                if (CONFIRMING_CLICK.matcher(entry.group(2)).find() || NOT_REFUSALS.contains(entry.group(1))) {
                    wrong.add(catalog.getFileName() + " " + entry.group(1));
                }
            }
        }
        assertThat(wrong).as("a prompt reads as an ERROR").isEmpty();
    }

    private static List<Path> catalogs() {
        Path folder = repoRoot().resolve("bukkit-adapter/src/messages/resources/messages");
        try (Stream<Path> files = Files.list(folder)) {
            List<Path> found = files.filter(
                            path -> path.getFileName().toString().startsWith("messages_"))
                    .sorted()
                    .toList();
            assertThat(found).as("the shipped catalogs").isNotEmpty();
            return found;
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to list " + folder, failure);
        }
    }

    private static List<String> read(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to read " + path, failure);
        }
    }

    /** The repository root, found by walking up from the working directory until the catalogs are in sight. */
    private static Path repoRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.isDirectory(candidate.resolve("bukkit-adapter/src/messages"))) {
            candidate = candidate.getParent();
        }
        assertThat(candidate).as("the repository root").isNotNull();
        return candidate;
    }
}
