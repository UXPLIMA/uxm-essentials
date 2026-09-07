package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import net.kyori.adventure.text.format.TextColor;

import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Where the colours are read from: one file for the server, and this plugin's own file on top of it.
 *
 * <p>A server usually runs more than one plugin from us and expects one look across them, so the theme lives
 * once at {@code plugins/uxmTheme/theme.conf}. A {@code theme.conf} in this plugin's own folder is read on
 * top of it, key by key, for the server that wants this plugin to read differently.
 *
 * <p>Neither file has to exist. A server that writes nothing keeps the colours this plugin ships, which is
 * what every server has seen until now.
 */
@NullMarked
public final class ThemeFile {

    private static final String FOLDER = "uxmTheme";

    private static final String FILE = "theme.conf";

    /** The character a tile's title line opens with; the library ships none, and this plugin has always drawn one. */
    private static final String TITLE_GLYPH = "\u25C6";

    private ThemeFile() {}

    /** The shared file, worked out from this plugin's data folder. */
    public static Path shared(Path dataFolder) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Path plugins = dataFolder.toAbsolutePath().getParent();
        Path root = plugins != null ? plugins : dataFolder;
        return root.resolve(FOLDER).resolve(FILE);
    }

    /**
     * The palette for {@code dataFolder}: the shared file with this plugin's own file over it.
     *
     * @throws ConfigurateException when a file exists and cannot be read, which an operator has to see
     * @throws IllegalArgumentException when a file holds something that is not a colour
     */
    public static Palette load(Path dataFolder) throws ConfigurateException {
        Objects.requireNonNull(dataFolder, "dataFolder");
        ConfigurationNode merged = node(dataFolder.resolve(FILE));
        merged.mergeFrom(node(shared(dataFolder)));
        return merged.empty() ? Palette.shipped() : Palette.from(merged);
    }

    /**
     * The palette for {@code dataFolder}, with an unreadable file reported as an unchecked failure.
     *
     * <p>This is the shape a reload step wants: the step is already wrapped in a reporter that turns a thrown
     * failure into a line an operator reads, so a checked exception here would only be caught and rethrown.
     */
    public static Palette read(Path dataFolder) {
        try {
            return load(dataFolder);
        } catch (ConfigurateException unreadable) {
            throw new IllegalStateException(
                    "cannot read " + shared(dataFolder) + ": " + unreadable.getMessage(), unreadable);
        }
    }

    /**
     * The same two files, read as the library's {@link Theme}.
     *
     * <p>uxmLib's menu engine draws a few parts of a window itself (a tile's title line, the editor's buttons) and
     * asks the host which colours and which furniture to use rather than deciding. It asks for its own type, so the
     * file this plugin already reads is read once more into it. One file, two readers, and an operator still edits
     * one place.
     *
     * <p>The shipped look is written underneath the operator's file rather than left to {@link Theme#defaults()},
     * which answers every role in a vanilla colour and names no glyph at all. That is correct for a library and
     * wrong for us: a server that wrote no file has always seen this plugin's own colours and the diamond at the
     * head of a tile, and a menu engine moving house must not take either away. So this is where the look stops
     * being a static somewhere inside a tile helper and becomes a value handed over on purpose.
     *
     * <p>An unreadable file yields the shipped look rather than throwing: the colours a window is drawn in must
     * never be the reason a menu fails to open, and {@link #read} already fails loudly on the same file for the
     * operator's benefit.
     */
    public static Theme theme(Path dataFolder) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        try {
            ConfigurationNode merged = node(dataFolder.resolve(FILE));
            merged.mergeFrom(node(shared(dataFolder)));
            merged.mergeFrom(shippedLook());
            return Theme.from(merged);
        } catch (ConfigurateException unreadable) {
            return Theme.from(shippedLook());
        }
    }

    /** The shipped look on its own, for a caller with no data folder to read from (a test, a fixture). */
    public static Theme shippedTheme() {
        return Theme.from(shippedLook());
    }

    /**
     * This plugin's own look as a node the library reads: every shipped role colour, and the one glyph a tile
     * title is drawn with. It sits under the operator's file, so a server that names {@code icon} gets its own
     * {@code icon} and keeps every role it did not name.
     */
    private static ConfigurationNode shippedLook() {
        CommentedConfigurationNode root = CommentedConfigurationNode.root();
        try {
            for (Map.Entry<String, TextColor> role : Palette.shipped().roles().entrySet()) {
                root.node("roles", role.getKey()).set(role.getValue().asHexString());
            }
            root.node("glyphs", "title").set(TITLE_GLYPH);
        } catch (SerializationException impossible) {
            throw new IllegalStateException("cannot write the shipped look", impossible);
        }
        return root;
    }

    private static ConfigurationNode node(Path file) throws ConfigurateException {
        if (!Files.isRegularFile(file)) {
            return CommentedConfigurationNode.root();
        }
        return HoconConfigurationLoader.builder().path(file).build().load();
    }
}
