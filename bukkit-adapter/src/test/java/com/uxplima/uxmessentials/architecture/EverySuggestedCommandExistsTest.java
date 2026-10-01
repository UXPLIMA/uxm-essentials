package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A command a shipped line tells a player to type is one this plugin registers.
 *
 * <p>The message of the day every new player reads on join told them to set a home with {@code /sethome}. There is
 * no such command: a home is set from the {@code /home} window, so the first thing the server taught a player failed.
 * The commands are read from {@code command-surface.txt}, which its own guard holds to the registered set.
 */
final class EverySuggestedCommandExistsTest {

    private static final Pattern SUGGESTED =
            Pattern.compile("(?:<cta>(?:<plain>)?|run_command:'|suggest_command:')/([a-z0-9]+)");
    private static final Pattern ROW = Pattern.compile("^([a-z0-9]+) ([a-z0-9]+) (\\S+)");

    @Test
    @DisplayName("every command a shipped line suggests is registered")
    void everySuggestedCommandIsRegistered() throws IOException {
        Set<String> commands = commands();
        assertThat(commands).describedAs("the inventory is read").hasSizeGreaterThan(100);

        List<String> missing = new ArrayList<>();
        Path adapter = ProductionSources.repoRoot().resolve("bukkit-adapter/src");
        try (Stream<Path> tree = Files.walk(adapter)) {
            for (Path file : tree.filter(path -> path.toString().endsWith(".conf"))
                    .filter(path -> !path.toString().contains("/test/"))
                    .toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher matcher = SUGGESTED.matcher(lines.get(i));
                    while (matcher.find()) {
                        if (!commands.contains(matcher.group(1))) {
                            missing.add(file.getFileName() + ":" + (i + 1) + " /" + matcher.group(1));
                        }
                    }
                }
            }
        }
        assertThat(missing).isEmpty();
    }

    private static Set<String> commands() {
        Path inventory = ProductionSources.repoRoot().resolve("bukkit-adapter/src/test/resources/command-surface.txt");
        Set<String> commands = new HashSet<>();
        try {
            for (String line : Files.readAllLines(inventory)) {
                Matcher row = ROW.matcher(line);
                if (!row.find()) {
                    continue;
                }
                commands.add(row.group(2));
                if (!row.group(3).equals("-")) {
                    commands.addAll(List.of(row.group(3).split(",")));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return commands;
    }
}
