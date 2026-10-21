package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    /** The glyph between a message's category word and its sentence. */
    private static final String SEPARATOR_GLYPH = "▶";

    /** The categories whose prefix reads in the good colour: every one a balance moves under. */
    private static final List<String> MONEY_CATEGORIES = List.of("economy", "bank", "loan", "trade", "vault");

    /** The glyph this plugin ships for a tile title, which a theme may replace. */
    public static String titleGlyph() {
        return TITLE_GLYPH;
    }

    /** The colour of every role this plugin ships, which an operator's file overrides role by role. */
    private static final Map<String, String> SHIPPED_COLOURS = shippedColours();

    private ThemeFile() {}

    /** The shared file, worked out from this plugin's data folder. */
    public static Path shared(Path dataFolder) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Path plugins = dataFolder.toAbsolutePath().getParent();
        Path root = plugins != null ? plugins : dataFolder;
        return root.resolve(FOLDER).resolve(FILE);
    }

    /**
     * The look for {@code dataFolder}, with a file that cannot be read reported rather than passed over.
     *
     * <p>This is the shape enabling and reloading want: an operator who broke the file must read why, and the step
     * is already wrapped in a reporter that turns a thrown failure into a line they read. The menu engine reads
     * through {@link #theme} instead, which never fails a window over a colour.
     *
     * @throws IllegalStateException when a file exists and cannot be read
     * @throws IllegalArgumentException when a file holds something that is not a colour
     */
    public static Theme read(Path dataFolder) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        try {
            ConfigurationNode merged = node(dataFolder.resolve(FILE));
            merged.mergeFrom(node(shared(dataFolder)));
            merged.mergeFrom(shippedLook());
            return Theme.from(merged);
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
     * never be the reason a menu fails to open, and {@link #read} fails loudly on the same file for the operator's
     * benefit.
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
            for (Map.Entry<String, String> role : SHIPPED_COLOURS.entrySet()) {
                root.node("roles", role.getKey()).set(role.getValue());
            }
            root.node("glyphs", "title").set(TITLE_GLYPH);
            root.node("glyphs", "separator").set(SEPARATOR_GLYPH);
            // Every money movement reads in the good colour, whichever feature raised it.
            for (String money : MONEY_CATEGORIES) {
                root.node("prefix", "categories", money).set("good");
            }
            // A heading reads in small capitals in every language whose letters all have one.
            root.node("label-small-caps").set("whole");
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

    private static Map<String, String> shippedColours() {
        Map<String, String> colours = new java.util.LinkedHashMap<>();
        colours.put("accent", "#38b6ff");
        colours.put("value", "#8fd9ff");
        colours.put("body", "#ffffff");
        colours.put("subtext", "#dde8f0");
        colours.put("muted", "#93a4b3");
        colours.put("dim", "#6b7886");
        colours.put("icon", "#8a93a1");
        colours.put("crumb", "#565f6b");
        colours.put("good", "#5be38c");
        colours.put("bad", "#ff6b6b");
        colours.put("warn", "#ffc93c");
        colours.put("money", "#ffc93c");
        colours.put("level", "#ffc93c");
        colours.put("cta", "#ffc93c");
        colours.put("info", "#4fd6e8");
        colours.put("rank", "#b68cff");
        colours.put("event", "#ff8fd0");
        // A plain title carries the reading colour rather than a colour of its own.
        colours.put("title", "#ffffff");
        // The lighter half of each ramp, used when the file names no wheel to take a neighbour from.
        colours.put("emerald", "#45d9a6");
        colours.put("rose", "#ff7aa8");
        colours.put("yellow", "#ffe15c");
        return Map.copyOf(colours);
    }
}
