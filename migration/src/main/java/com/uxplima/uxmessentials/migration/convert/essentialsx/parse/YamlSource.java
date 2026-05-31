package com.uxplima.uxmessentials.migration.convert.essentialsx.parse;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * The single seam where SnakeYAML/Configurate touches the disk for the EssentialsX source. Every
 * parser loads its file through here, so the foreign YAML codec is confined to one class and the rest
 * of {@code parse/} works against {@link ConfigurationNode} value reads. This is the parsing-stays-in-
 * the-adapter boundary (docs/12-migration §2): no domain type appears in this file, and Configurate is
 * a {@code :migration}-only dependency that never reaches {@code :core}.
 */
@NullMarked
public final class YamlSource {

    private YamlSource() {}

    /** Load a YAML file into a Configurate node tree. Throws {@link IOException} on a malformed file. */
    public static ConfigurationNode load(Path file) throws IOException {
        YamlConfigurationLoader loader =
                YamlConfigurationLoader.builder().path(file).build();
        return loader.load();
    }

    /** Load YAML from an arbitrary reader (used by fixtures and the kits.yml single-document layout). */
    public static ConfigurationNode load(Reader reader) throws IOException {
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .source(() -> bufferedOf(reader))
                .build();
        return loader.load();
    }

    private static java.io.BufferedReader bufferedOf(Reader reader) {
        return reader instanceof java.io.BufferedReader buffered ? buffered : new java.io.BufferedReader(reader);
    }

    /** An empty root node, for the rare case a parser needs a blank tree to fall back to. */
    public static ConfigurationNode empty() {
        return CommentedConfigurationNode.root();
    }
}
