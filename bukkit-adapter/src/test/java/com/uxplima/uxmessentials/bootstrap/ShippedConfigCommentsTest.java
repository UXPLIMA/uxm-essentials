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

    /**
     * Every settings file a module ships: only each module's own {@code src/main/resources} is walked, so build output,
     * the Git and Gradle folders and any checkout nested inside this one are never read.
     */
    private static List<Path> shippedSettingsFiles(Path repository) throws IOException {
        List<Path> found = new ArrayList<>();
        try (Stream<Path> modules = Files.list(repository)) {
            for (Path module : modules.filter(Files::isDirectory).sorted().toList()) {
                Path resources = module.resolve("src/main/resources");
                if (!Files.isDirectory(resources)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(resources)) {
                    files.filter(Files::isRegularFile)
                            .filter(file -> isSettingsFile(slashed(resources.relativize(file))))
                            .sorted()
                            .forEach(found::add);
                }
            }
        }
        return found;
    }

    /** A settings file, by its path under {@code src/main/resources}: not a catalogue and not a menu layout. */
    private static boolean isSettingsFile(String name) {
        return name.endsWith(".conf")
                && !name.startsWith("messages/")
                && !name.contains("/gui/")
                && !name.startsWith("menus/");
    }

    /** The checkout the build passes in, so the scan does not depend on where the test JVM starts. */
    private static Path repositoryRoot() {
        @Nullable String root = System.getProperty("uxm.repositoryRoot");
        if (root == null) {
            throw new IllegalStateException("the build passes uxm.repositoryRoot to the test JVM");
        }
        return Path.of(root).toAbsolutePath().normalize();
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
