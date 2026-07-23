package com.uxplima.uxmessentials.velocity.commandcontrol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Loads the proxy's {@code config.conf} into a Configurate tree. On first run it extracts the bundled
 * default (with {@code command-control.enabled = false}) into the plugin data directory, so an operator
 * has a documented template to edit; subsequent loads parse the on-disk file. Configurate types are
 * confined to this loader and {@link NodeConfigStore} - the rest of the command-control adapter reads the
 * typed {@code :core} {@code CommandControlConfig}.
 *
 * <p>The loader takes a plain {@link Path} so it is exercised in tests without a live proxy.
 */
public final class CommandControlConfigLoader {

    private static final String FILE_NAME = "config.conf";
    private static final String RESOURCE = "/config.conf";

    private CommandControlConfigLoader() {}

    /** Extract the default if missing, then parse {@code config.conf} under {@code dataDirectory}. */
    public static ConfigurationNode load(Path dataDirectory) throws ConfigurateException {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Path file = dataDirectory.resolve(FILE_NAME);
        extractDefaultIfMissing(file);
        return HoconConfigurationLoader.builder().path(file).build().load();
    }

    private static void extractDefaultIfMissing(Path file) {
        if (Files.exists(file)) {
            return;
        }
        try (InputStream resource = CommandControlConfigLoader.class.getResourceAsStream(RESOURCE)) {
            Files.createDirectories(Objects.requireNonNull(file.getParent(), "parent"));
            if (resource == null) {
                // No bundled default on the classpath (should not happen); leave the file absent so the
                // parser returns an empty tree and every value falls back to its coded default.
                return;
            }
            Files.copy(resource, file);
        } catch (IOException failure) {
            throw new UncheckedExtractException("failed to extract default " + FILE_NAME, failure);
        }
    }

    /** Wraps an extraction I/O failure as unchecked so a caller can report it with context and continue. */
    public static final class UncheckedExtractException extends RuntimeException {
        UncheckedExtractException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
