package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.LocaleCatalog;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The {@link LocaleCatalog} that loads each {@code messages/messages_<lang>.conf} as a flat map
 * of catalog key to MiniMessage template. Catalog keys are quoted, dot-separated strings
 * ({@code "home.teleported"}), so they are read as literal HOCON map keys rather than nested paths.
 *
 * <p>The fallback chain is per key: a key missing from the requested locale falls back to English, and
 * a key missing from English too falls back to the key's own name so a message is never blank. English
 * is always loaded; additional locales are loaded on demand and cached.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. Per-locale tables are immutable maps held in a
 * {@link ConcurrentHashMap}; a locale loads at most once and is read lock-free thereafter.
 */
@NullMarked
public final class HoconLocaleCatalog implements LocaleCatalog {

    private static final String RESOURCE_DIR = "messages/";

    private final Logger log;
    private final Path messagesDir;
    private final ConcurrentHashMap<String, Map<String, String>> byLanguage = new ConcurrentHashMap<>();

    /**
     * @param messagesDir the on-disk {@code messages} directory; a {@code messages_<lang>.conf}
     *     present there is read in preference to the bundled copy, so operator edits take effect.
     */
    public HoconLocaleCatalog(Logger log, Path messagesDir) {
        this.log = Objects.requireNonNull(log, "log");
        this.messagesDir = Objects.requireNonNull(messagesDir, "messagesDir");
        byLanguage.put(Locale.ENGLISH.getLanguage(), loadLanguage(Locale.ENGLISH.getLanguage()));
    }

    @Override
    public String template(Locale locale, MessageKey key) {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(key, "key");
        String fromLocale = tableFor(locale.getLanguage()).get(key.key());
        if (fromLocale != null) {
            return fromLocale;
        }
        String fromEnglish = tableFor(Locale.ENGLISH.getLanguage()).get(key.key());
        return fromEnglish != null ? fromEnglish : key.key();
    }

    @Override
    public Set<Locale> loadedLocales() {
        Set<Locale> locales = new LinkedHashSet<>();
        locales.add(Locale.ENGLISH);
        for (String language : byLanguage.keySet()) {
            locales.add(Locale.forLanguageTag(language));
        }
        return Set.copyOf(locales);
    }

    private Map<String, String> tableFor(String language) {
        return byLanguage.computeIfAbsent(language, this::loadLanguage);
    }

    private Map<String, String> loadLanguage(String language) {
        Path onDisk = messagesDir.resolve("messages_" + language + ".conf");
        if (Files.isRegularFile(onDisk)) {
            return parse(HoconConfigurationLoader.builder().path(onDisk).build(), onDisk.toString());
        }
        String resource = RESOURCE_DIR + "messages_" + language + ".conf";
        if (getClass().getClassLoader().getResource(resource) == null) {
            return Map.of();
        }
        return parse(
                HoconConfigurationLoader.builder()
                        .source(() -> openReader(resource))
                        .build(),
                resource);
    }

    private Map<String, String> parse(HoconConfigurationLoader loader, String origin) {
        ConfigurationNode root;
        try {
            root = loader.load();
        } catch (ConfigurateException failure) {
            log.error("failed to load message catalog " + origin, failure);
            return Map.of();
        }
        Map<String, String> table = new java.util.HashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                root.childrenMap().entrySet()) {
            String value = entry.getValue().getString();
            if (value != null) {
                table.put(String.valueOf(entry.getKey()), value);
            }
        }
        return Map.copyOf(table);
    }

    private BufferedReader openReader(String resource) throws java.io.IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new java.io.FileNotFoundException(resource);
        }
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
}
