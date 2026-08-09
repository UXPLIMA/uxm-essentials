package com.uxplima.uxmessentials.rest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Reads {@code config/rest.conf} into a {@link RestConfig}, extracting the shipped default on first run.
 *
 * <p>Configurate stops here; nothing else in the add-on sees it. A file that will not parse yields
 * {@link RestConfig#DORMANT} rather than throwing: a typo in a config should leave the listener off and a line in
 * the log, not stop the server from enabling the plugin.
 */
public final class RestConfigLoader {

    private static final String FILE_NAME = "rest.conf";
    private static final String RESOURCE = "/rest.conf";

    private RestConfigLoader() {}

    /** Extract the default if it is missing, then read what is there. */
    public static RestConfig load(Path dataFolder) throws ConfigurateException {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Path file = dataFolder.resolve("config").resolve(FILE_NAME);
        extractDefaultIfMissing(file);
        return parse(HoconConfigurationLoader.builder().path(file).build().load());
    }

    /** Read an already-loaded tree, which is what the tests hand it. */
    static RestConfig parse(ConfigurationNode root) {
        boolean enabled = root.node("enabled").getBoolean(RestConfig.DORMANT.enabled());
        String bind = root.node("bind").getString(RestConfig.DORMANT.bind());
        int port = root.node("port").getInt(RestConfig.DORMANT.port());
        int perMinute = root.node("requests-per-minute").getInt(RestConfig.DORMANT.requestsPerMinute());
        if (port < 1 || port > 65535 || perMinute < 1) {
            return RestConfig.DORMANT;
        }
        return new RestConfig(enabled, bind, port, perMinute);
    }

    private static void extractDefaultIfMissing(Path file) {
        if (Files.exists(file)) {
            return;
        }
        try (InputStream resource = RestConfigLoader.class.getResourceAsStream(RESOURCE)) {
            Files.createDirectories(Objects.requireNonNull(file.getParent(), "parent"));
            if (resource == null) {
                throw new IllegalStateException("the bundled " + FILE_NAME + " is missing from the jar");
            }
            Files.copy(resource, file);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to extract the default " + FILE_NAME, failure);
        }
    }
}
