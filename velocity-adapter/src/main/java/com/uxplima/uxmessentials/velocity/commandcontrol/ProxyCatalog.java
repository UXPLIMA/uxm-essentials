package com.uxplima.uxmessentials.velocity.commandcontrol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The proxy's own message catalogue: the few lines the proxy says itself, in the same twelve languages and the same
 * {@code messages/messages_<lang>.conf} shape as the backend's, so an operator translates and restyles them the way
 * they already do there.
 *
 * <p>The proxy cannot read the backend's files, which live on other machines. Each language ships in the jar and is
 * copied into the plugin folder on first run; a line the operator's copy carries wins over the shipped one, and a line
 * it lacks still reads, so a file from an older version keeps working when a line is added. A reader whose client
 * language has no file reads English.
 */
public final class ProxyCatalog {

    /** The languages a file ships for, the same set the backend carries. */
    public static final List<String> LANGUAGES =
            List.of("en", "de", "es", "fr", "ja", "ko", "pl", "pt", "ru", "tr", "uk", "zh");

    private static final String FALLBACK = "en";
    private static final String DIRECTORY = "messages";

    private final Map<String, Map<String, String>> byLanguage;

    /** @param byLanguage each language code's lines, keyed by catalogue key */
    public ProxyCatalog(Map<String, Map<String, String>> byLanguage) {
        Objects.requireNonNull(byLanguage, "byLanguage");
        Map<String, Map<String, String>> copy = new HashMap<>();
        byLanguage.forEach((language, lines) -> copy.put(language, Map.copyOf(lines)));
        this.byLanguage = Map.copyOf(copy);
    }

    /**
     * The shipped files with the operator's copies under {@code dataDirectory} laid over them, writing out any copy
     * that is missing so there is a file to edit.
     *
     * @throws ConfigurateException when a file does not parse
     */
    public static ProxyCatalog load(Path dataDirectory) throws ConfigurateException {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Map<String, Map<String, String>> byLanguage = new HashMap<>();
        for (String language : LANGUAGES) {
            String name = "messages_" + language + ".conf";
            Map<String, String> lines = new HashMap<>(shipped(language));
            Path onDisk = dataDirectory.resolve(DIRECTORY).resolve(name);
            extractIfMissing(onDisk, resource(language));
            if (Files.isRegularFile(onDisk)) {
                lines.putAll(
                        parse(HoconConfigurationLoader.builder().path(onDisk).build()));
            }
            byLanguage.put(language, lines);
        }
        return new ProxyCatalog(byLanguage);
    }

    /** The lines the jar ships for {@code language}, or none when it ships no such file. */
    public static Map<String, String> shipped(String language) throws ConfigurateException {
        String resource = resource(language);
        if (ProxyCatalog.class.getResource(resource) == null) {
            return Map.of();
        }
        return parse(HoconConfigurationLoader.builder()
                .source(() -> new BufferedReader(new InputStreamReader(open(resource), StandardCharsets.UTF_8)))
                .build());
    }

    /** Whether any language carries {@code key}, which is what makes an {@code @key} value a reference. */
    public boolean declares(String key) {
        Objects.requireNonNull(key, "key");
        return byLanguage.values().stream().anyMatch(lines -> lines.containsKey(key));
    }

    /** {@code key} in {@code locale}'s language, then in English, or empty when neither carries it. */
    public String line(@Nullable Locale locale, String key) {
        Objects.requireNonNull(key, "key");
        String language = locale == null ? FALLBACK : locale.getLanguage().toLowerCase(Locale.ROOT);
        String own = byLanguage.getOrDefault(language, Map.of()).get(key);
        if (own != null) {
            return own;
        }
        return byLanguage.getOrDefault(FALLBACK, Map.of()).getOrDefault(key, "");
    }

    private static String resource(String language) {
        return "/" + DIRECTORY + "/messages_" + language + ".conf";
    }

    private static InputStream open(String resource) throws IOException {
        InputStream in = ProxyCatalog.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IOException("the jar ships no " + resource);
        }
        return in;
    }

    private static void extractIfMissing(Path file, String resource) {
        if (Files.exists(file)) {
            return;
        }
        try (InputStream in = ProxyCatalog.class.getResourceAsStream(resource)) {
            if (in == null) {
                return;
            }
            Files.createDirectories(Objects.requireNonNull(file.getParent(), "parent"));
            Files.copy(in, file);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to write " + file, failure);
        }
    }

    private static Map<String, String> parse(HoconConfigurationLoader loader) throws ConfigurateException {
        ConfigurationNode root = loader.load();
        Map<String, String> lines = new HashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                root.childrenMap().entrySet()) {
            String value = entry.getValue().getString();
            if (value != null) {
                lines.put(String.valueOf(entry.getKey()), value);
            }
        }
        return lines;
    }
}
