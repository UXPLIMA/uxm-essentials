package com.uxplima.uxmessentials.shared.adapter.outbound.style;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

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

    private static ConfigurationNode node(Path file) throws ConfigurateException {
        if (!Files.isRegularFile(file)) {
            return CommentedConfigurationNode.root();
        }
        return HoconConfigurationLoader.builder().path(file).build().load();
    }
}
