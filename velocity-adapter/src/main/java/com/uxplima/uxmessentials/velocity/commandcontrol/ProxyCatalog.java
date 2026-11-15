package com.uxplima.uxmessentials.velocity.commandcontrol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

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
 *
 * <p>Loading never fails. The lines decorate a command gate, and a typo in one translation must not switch off command
 * hiding and the spam guard for the whole network: an operator file that does not parse falls back to that language's
 * shipped lines, a shipped file that does not parse falls back to English, and a folder that cannot be written is
 * passed over. Each of those is reported as a warning.
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
     * that is missing so there is a file to edit. Whatever cannot be read is passed to {@code warn} and skipped.
     */
    public static ProxyCatalog load(Path dataDirectory, Consumer<String> warn) {
        return load(dataDirectory, ProxyCatalog::bundledText, warn);
    }

    /**
     * {@link #load(Path, Consumer)} with the shipped files read through {@code shipped}, which answers a language code
     * with that file's text, or empty when the jar ships none.
     */
    static ProxyCatalog load(Path dataDirectory, Function<String, Optional<String>> shipped, Consumer<String> warn) {
        Objects.requireNonNull(dataDirectory, "dataDirectory");
        Objects.requireNonNull(shipped, "shipped");
        Objects.requireNonNull(warn, "warn");
        Map<String, String> english = shippedLines(FALLBACK, shipped, warn).orElse(Map.of());
        Map<String, Map<String, String>> byLanguage = new HashMap<>();
        for (String language : LANGUAGES) {
            Map<String, String> lines = new HashMap<>(
                    language.equals(FALLBACK)
                            ? english
                            : shippedLines(language, shipped, warn).orElse(english));
            Path onDisk = dataDirectory.resolve(DIRECTORY).resolve("messages_" + language + ".conf");
            shipped.apply(language).ifPresent(text -> extractIfMissing(onDisk, text, warn));
            if (Files.isRegularFile(onDisk)) {
                try {
                    lines.putAll(parse(
                            HoconConfigurationLoader.builder().path(onDisk).build()));
                } catch (ConfigurateException broken) {
                    warn.accept("could not read " + onDisk + ", so " + language + " reads the shipped lines until it"
                            + " is fixed: " + broken.getMessage());
                }
            }
            byLanguage.put(language, lines);
        }
        return new ProxyCatalog(byLanguage);
    }

    /** The lines the jar ships for {@code language}, or none when it ships no such file. */
    public static Map<String, String> shipped(String language) throws ConfigurateException {
        Optional<String> text = bundledText(language);
        return text.isEmpty() ? Map.of() : parse(loader(text.get()));
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

    private static Optional<Map<String, String>> shippedLines(
            String language, Function<String, Optional<String>> shipped, Consumer<String> warn) {
        Optional<String> text = shipped.apply(language);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(parse(loader(text.get())));
        } catch (ConfigurateException broken) {
            warn.accept("the shipped " + language + " message file does not parse, so " + language + " reads English: "
                    + broken.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<String> bundledText(String language) {
        String resource = "/" + DIRECTORY + "/messages_" + language + ".conf";
        try (InputStream in = ProxyCatalog.class.getResourceAsStream(resource)) {
            return in == null ? Optional.empty() : Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    private static void extractIfMissing(Path file, String text, Consumer<String> warn) {
        if (Files.exists(file)) {
            return;
        }
        try {
            Files.createDirectories(Objects.requireNonNull(file.getParent(), "parent"));
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            warn.accept("could not write " + file + ", so its shipped lines are read from the jar: "
                    + failure.getMessage());
        }
    }

    private static HoconConfigurationLoader loader(String text) {
        return HoconConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(text)))
                .build();
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
