package com.uxplima.uxmessentials.i18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Reads the bundled {@code messages/messages_<lang>.conf} catalogs as flat key sets, shared by
 * the locale-parity Gradle gate ({@link LocaleParityCheck}) and the JUnit matrix guard
 * ({@code MessageKeyLocaleParityDriftTest}). The {@code prefix} key is the sink's injected {@code
 * <prefix>} tag, not a player-facing message, so it is excluded from the parity comparison.
 */
final class CatalogKeys {

    static final String PREFIX_KEY = "prefix";

    /** Keys under this prefix configure the catalog (its typography); they are not player-facing messages. */
    static final String META_PREFIX = "meta.";

    private static final String RESOURCE_DIR = "messages/";

    private CatalogKeys() {}

    /** The resource path of the catalog for {@code language} (e.g. {@code en}, {@code tr}). */
    static String resource(String language) {
        return RESOURCE_DIR + "messages_" + language + ".conf";
    }

    /** Whether a catalog file exists on the classpath for {@code language}. */
    static boolean exists(String language) {
        return CatalogKeys.class.getClassLoader().getResource(resource(language)) != null;
    }

    /** The catalog key set for {@code language}, excluding the {@code prefix} tag. Never null. */
    static Set<String> read(String language) {
        Set<String> keys = new LinkedHashSet<>();
        try (InputStream in = CatalogKeys.class.getClassLoader().getResourceAsStream(resource(language))) {
            if (in == null) {
                throw new IllegalStateException("missing catalog: " + resource(language));
            }
            ConfigurationNode root = load(in);
            for (Object key : root.childrenMap().keySet()) {
                String name = String.valueOf(key);
                if (!PREFIX_KEY.equals(name) && !name.startsWith(META_PREFIX)) {
                    keys.add(name);
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to read catalog " + resource(language), failure);
        }
        return keys;
    }

    /** The language codes of every shipped locale catalog beyond {@code en}, in sorted order. */
    static Set<String> shippedLanguages() {
        Set<String> languages = new LinkedHashSet<>();
        languages.add(Locale.ENGLISH.getLanguage());
        // The shipped extra locales are enumerated here so the gate has a deterministic set to check
        // without scanning the jar; adding a locale file means adding its code to this list.
        for (String candidate : new String[] {"tr", "de", "ru", "es", "pt", "fr", "pl", "uk", "zh", "ja", "ko"}) {
            if (exists(candidate)) {
                languages.add(candidate);
            }
        }
        return languages;
    }

    private static ConfigurationNode load(InputStream in) {
        try {
            return HoconConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .build()
                    .load();
        } catch (ConfigurateException parseFailure) {
            throw new IllegalStateException("failed to parse a message catalog", parseFailure);
        }
    }
}
