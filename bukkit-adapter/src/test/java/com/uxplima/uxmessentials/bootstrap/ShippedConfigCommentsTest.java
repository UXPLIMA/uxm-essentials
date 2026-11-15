package com.uxplima.uxmessentials.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Every block of every settings file the plugin ships says what it is for, read the way the plugin reads it: through
 * the HOCON loader, which keeps the comment written above a key on that key's node. That is also the comment the
 * defaults merge copies into an operator's file along with a setting an update adds, so a block without one arrives
 * there unexplained.
 *
 * <p>Message catalogues and menu layouts are left out. A catalogue is one translated line per key, and a menu layout
 * is a screen whose tiles say what they are through their own name, slot and material.
 */
class ShippedConfigCommentsTest {

    @Test
    void everyShippedBlockHasACommentAboveIt() throws IOException {
        Path repository = repositoryRoot();
        List<Path> files = shippedSettingsFiles(repository);
        List<String> uncommented = new ArrayList<>();

        for (Path file : files) {
            collectUncommented(load(file), slashed(repository.relativize(file)), "", uncommented);
        }

        assertThat(files).as("shipped settings files").hasSizeGreaterThan(40);
        assertThat(uncommented)
                .as("blocks with no comment above them; say what the block does and what a wrong value causes")
                .isEmpty();
    }

    @Test
    void aBlockWithoutACommentIsCaught() throws IOException {
        CommentedConfigurationNode root = parse("""
                # Documented.
                documented {
                  value = 1
                }
                bare {
                  value = 1
                }
                """);
        List<String> uncommented = new ArrayList<>();

        collectUncommented(root, "sample.conf", "", uncommented);

        assertThat(uncommented).containsExactly("sample.conf: bare");
    }

    @Test
    void aBlockWrittenAsADottedKeyIsDocumentedByTheCommentAboveThatLine() throws IOException {
        CommentedConfigurationNode root = parse("""
                # The groups.
                groups {
                  # The first group.
                  first.enabled = true
                  second.enabled = true
                }
                """);
        List<String> uncommented = new ArrayList<>();

        collectUncommented(root, "sample.conf", "", uncommented);

        assertThat(uncommented).containsExactly("sample.conf: groups.second");
    }

    /**
     * Adds to {@code uncommented} every block under {@code node} with no comment. A block written as a dotted key on
     * one line ({@code first.enabled = true}) puts the comment above that line on its one setting, so that counts too.
     */
    private static void collectUncommented(
            CommentedConfigurationNode node, String file, String path, List<String> uncommented) {
        for (Map.Entry<Object, CommentedConfigurationNode> entry :
                node.childrenMap().entrySet()) {
            CommentedConfigurationNode child = entry.getValue();
            if (!child.isMap()) {
                continue;
            }
            String at = path.isEmpty() ? String.valueOf(entry.getKey()) : path + "." + entry.getKey();
            if (!commented(child) && !writtenAsOneDottedSetting(child)) {
                uncommented.add(file + ": " + at);
            }
            collectUncommented(child, file, at, uncommented);
        }
    }

    private static boolean writtenAsOneDottedSetting(CommentedConfigurationNode block) {
        Map<Object, CommentedConfigurationNode> children = block.childrenMap();
        return children.size() == 1 && children.values().stream().allMatch(only -> !only.isMap() && commented(only));
    }

    private static boolean commented(CommentedConfigurationNode node) {
        @Nullable String comment = node.comment();
        return comment != null && !comment.isBlank();
    }

    private static List<Path> shippedSettingsFiles(Path repository) throws IOException {
        try (Stream<Path> walk = Files.walk(repository)) {
            return walk.filter(Files::isRegularFile)
                    .filter(file -> isShippedSettingsFile(slashed(repository.relativize(file))))
                    .sorted()
                    .toList();
        }
    }

    private static boolean isShippedSettingsFile(String name) {
        return name.endsWith(".conf")
                && name.contains("/src/main/resources/")
                && !name.contains("/build/")
                && !name.contains("/messages/")
                && !name.contains("/gui/")
                && !name.contains("/menus/");
    }

    /** The checkout this test runs in: the nearest folder above the working directory that holds the build settings. */
    private static Path repositoryRoot() {
        Path at = Path.of("").toAbsolutePath();
        while (!Files.exists(at.resolve("settings.gradle.kts"))) {
            @Nullable Path parent = at.getParent();
            if (parent == null) {
                throw new IllegalStateException(
                        "no settings.gradle.kts above " + Path.of("").toAbsolutePath());
            }
            at = parent;
        }
        return at;
    }

    private static String slashed(Path path) {
        return path.toString().replace(File.separatorChar, '/');
    }

    private static CommentedConfigurationNode load(Path file) throws IOException {
        return HoconConfigurationLoader.builder()
                .source(() -> Files.newBufferedReader(file))
                .build()
                .load();
    }

    private static CommentedConfigurationNode parse(String text) throws IOException {
        return HoconConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(text)))
                .build()
                .load();
    }
}
