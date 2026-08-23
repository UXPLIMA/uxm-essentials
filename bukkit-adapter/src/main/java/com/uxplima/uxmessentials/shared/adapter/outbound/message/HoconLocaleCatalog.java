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
import java.util.concurrent.atomic.AtomicReference;

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
 * <p>Each locale is loaded as a merge of two layers: the bundled classpath default
 * ({@code messages/messages_<lang>.conf} inside the jar) as the base, and the on-disk
 * {@code messages_<lang>.conf} the operator edits layered over it as the per-key override. So a key an update
 * adds to the bundled catalog still resolves on a server whose on-disk file predates it (from the bundled
 * default), while an operator's edit to a key still wins. The fallback chain is then per key: operator disk
 * override, then bundled default (both captured in the requested locale's merged table), then English, then the
 * key's own name so a message is never blank. English is always loaded; additional locales load on demand and cache.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>immutable-snapshot</b>. Every locale table and the outer language map are immutable and the complete
 * generation is held in an {@link AtomicReference}; reload publishes all loaded languages in one swap.
 */
@NullMarked
public final class HoconLocaleCatalog implements LocaleCatalog {

    private static final String RESOURCE_DIR = "messages/";

    /** Keys under this prefix configure the catalog itself; they are not messages and are never handed out. */
    private static final String META_PREFIX = "meta.";

    /** Whether a catalog's own text is written in small capitals when it is rendered. */
    private static final String SMALL_CAPS_KEY = META_PREFIX + "small-caps";

    private final Logger log;
    private final Path messagesDir;
    private final AtomicReference<Map<String, Map<String, String>>> byLanguage;

    /**
     * @param messagesDir the on-disk {@code messages} directory; a {@code messages_<lang>.conf}
     *     present there is layered over the bundled copy as a per-key override, so operator edits take effect
     *     while a key present only in the bundled default still resolves.
     */
    public HoconLocaleCatalog(Logger log, Path messagesDir) {
        this.log = Objects.requireNonNull(log, "log");
        this.messagesDir = Objects.requireNonNull(messagesDir, "messagesDir");
        this.byLanguage = new AtomicReference<>(
                Map.of(Locale.ENGLISH.getLanguage(), loadLanguage(Locale.ENGLISH.getLanguage(), false)));
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
        for (String language : snapshot().keySet()) {
            locales.add(Locale.forLanguageTag(language));
        }
        return Set.copyOf(locales);
    }

    @Override
    public void reload() {
        // Parse every currently loaded locale into a private candidate first. One malformed disk catalog rejects
        // the whole pass, leaving every language on the same last-known-good generation.
        Map<String, Map<String, String>> current = snapshot();
        Map<String, Map<String, String>> candidate = new java.util.HashMap<>();
        for (String language : current.keySet()) {
            candidate.put(language, loadLanguage(language, true));
        }
        candidate.computeIfAbsent(Locale.ENGLISH.getLanguage(), language -> loadLanguage(language, true));
        byLanguage.set(Map.copyOf(candidate));
    }

    private Map<String, String> tableFor(String language) {
        while (true) {
            Map<String, Map<String, String>> current = snapshot();
            Map<String, String> found = current.get(language);
            if (found != null) {
                return found;
            }
            Map<String, String> loaded = loadLanguage(language, false);
            Map<String, Map<String, String>> expanded = new java.util.HashMap<>(current);
            expanded.put(language, loaded);
            if (byLanguage.compareAndSet(current, Map.copyOf(expanded))) {
                return loaded;
            }
        }
    }

    private Map<String, Map<String, String>> snapshot() {
        return Objects.requireNonNull(byLanguage.get(), "byLanguage");
    }

    private Map<String, String> loadLanguage(String language, boolean failOnReadError) {
        Map<String, String> bundled = loadBundled(language, failOnReadError);
        Map<String, String> onDisk = loadOnDisk(language, failOnReadError);
        // Bundled default is the base; the operator's on-disk file overrides it per key. A key the operator kept
        // resolves from disk; a key an update added but the disk file never had resolves from the bundled default.
        Map<String, String> merged = new java.util.HashMap<>(bundled);
        merged.putAll(onDisk);
        return styled(merged, language);
    }

    /**
     * The table a caller sees: the {@code meta.} settings are consumed here, and when this catalog asks for the
     * small-capital typography its text is converted once, now, rather than on every message it renders. A
     * catalog whose language has no small capitals (Cyrillic, Chinese, Japanese, Korean) or whose letters would
     * only half convert (Turkish, German, Polish) simply leaves the setting off and keeps its own letters.
     */
    private static Map<String, String> styled(Map<String, String> merged, String language) {
        boolean smallCaps = Boolean.parseBoolean(merged.getOrDefault(
                SMALL_CAPS_KEY, String.valueOf(Locale.ENGLISH.getLanguage().equals(language))));
        Map<String, String> table = new java.util.HashMap<>(merged.size());
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            if (entry.getKey().startsWith(META_PREFIX)) {
                continue;
            }
            table.put(entry.getKey(), smallCaps ? SmallCapsTemplates.apply(entry.getValue()) : entry.getValue());
        }
        return Map.copyOf(table);
    }

    /** The bundled classpath default for {@code language}, the base layer, or empty when the jar ships none. */
    private Map<String, String> loadBundled(String language, boolean failOnReadError) {
        String resource = RESOURCE_DIR + "messages_" + language + ".conf";
        if (getClass().getClassLoader().getResource(resource) == null) {
            return Map.of();
        }
        return parse(
                HoconConfigurationLoader.builder()
                        .source(() -> openReader(resource))
                        .build(),
                resource,
                failOnReadError);
    }

    /** The operator's on-disk file for {@code language}, the override layer, or empty when it is absent. */
    private Map<String, String> loadOnDisk(String language, boolean failOnReadError) {
        Path onDisk = messagesDir.resolve("messages_" + language + ".conf");
        if (!Files.isRegularFile(onDisk)) {
            return Map.of();
        }
        return parse(HoconConfigurationLoader.builder().path(onDisk).build(), onDisk.toString(), failOnReadError);
    }

    private Map<String, String> parse(HoconConfigurationLoader loader, String origin, boolean failOnReadError) {
        ConfigurationNode root;
        try {
            root = loader.load();
        } catch (ConfigurateException failure) {
            log.error("failed to load message catalog " + origin, failure);
            if (failOnReadError) {
                throw new IllegalStateException("failed to parse message catalog " + origin, failure);
            }
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
