package com.uxplima.uxmessentials.shared.adapter.outbound.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.command.CommandOverride;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Reads the root {@code commands.conf} file into the override map the
 * {@link com.uxplima.uxmessentials.shared.application.command.CommandCatalog} resolves against. Each file
 * holds a {@code commands { <id> { ... } }} block; ids are taken as raw HOCON map keys.
 *
 * <p>The historical {@code commands/commands.conf} layout is moved to the root atomically on first load when
 * it is the directory's only config file. Installations that deliberately split the catalog across several
 * {@code commands/*.conf} files remain readable as a deprecated fallback rather than losing operator edits.
 * A root file is always authoritative when both layouts exist. A malformed file is logged and skipped rather
 * than failing plugin enable; validating the id shape is the resolver's job.
 *
 * <p>Only {@code .conf} files sitting directly in {@code commands/} were ever part of that old layout, and
 * that is exactly what this looks for. The directory itself is no longer ours alone: the customcommands
 * module keeps its definitions in {@code commands/custom/}, so on a fresh install the directory exists and
 * holds nothing of ours. Treating its mere presence as a legacy catalog is what made every first boot print a
 * deprecation notice telling the operator to move files that were not there.
 */
@NullMarked
public final class CommandCatalogConfig {

    private static final String COMMANDS_FILE = "commands.conf";
    private static final String LEGACY_COMMANDS_DIR = "commands";
    private static final String CONF_SUFFIX = ".conf";
    private static final String GUI_DEFAULT_KEY = "gui-default";
    private static final boolean GUI_DEFAULT = true;

    private final Path dataFolder;
    private final Logger log;

    public CommandCatalogConfig(Path dataFolder, Logger log) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * The merged override map keyed by command id and the global {@code gui-default}: the bare-opens-GUI
     * behaviour every command inherits unless its own {@code gui} flag opts in or out.
     */
    public record Loaded(Map<String, CommandOverride> overrides, boolean guiDefault) {
        public Loaded {
            overrides = Map.copyOf(overrides);
        }
    }

    /** Load the root catalog (or the deprecated split-file fallback) into a map keyed by command id. */
    public Map<String, CommandOverride> load() {
        return loadAll().overrides();
    }

    /** Load the catalog into the override map plus the global {@code gui-default}. */
    public Loaded loadAll() {
        Path rootFile = dataFolder.resolve(COMMANDS_FILE);
        Path dir = dataFolder.resolve(LEGACY_COMMANDS_DIR);
        if (Files.isRegularFile(rootFile)) {
            warnIfLegacyAlsoExists(dir, rootFile);
            return readFiles(List.of(rootFile));
        }
        if (!Files.isDirectory(dir)) {
            return new Loaded(Map.of(), GUI_DEFAULT);
        }
        if (removeEmptyLegacyDirectory(dir)) {
            return new Loaded(Map.of(), GUI_DEFAULT);
        }
        if (legacyConfFiles(dir).isEmpty()) {
            // The directory belongs to something else now (customcommands writes commands/custom/). There is no
            // catalog here to deprecate, so there is nothing to say.
            return new Loaded(Map.of(), GUI_DEFAULT);
        }
        if (migrateSingleLegacyFile(dir, rootFile)) {
            return readFiles(List.of(rootFile));
        }
        log.warn("legacy command catalog directory {} is deprecated; move its files into {}", dir, rootFile);
        return readLegacyDirectory(dir);
    }

    private Loaded readLegacyDirectory(Path dir) {
        return readFiles(legacyConfFiles(dir));
    }

    /**
     * The {@code .conf} files directly inside {@code commands/}, in name order. Subdirectories are not part of
     * the old layout and belong to whoever put them there, so they are not descended into.
     */
    private List<Path> legacyConfFiles(Path dir) {
        try (Stream<Path> listed = Files.list(dir)) {
            return listed.filter(CommandCatalogConfig::isConfFile)
                    .sorted(Comparator.comparing(Path::getFileName))
                    .toList();
        } catch (IOException failure) {
            log.error("failed to list command override directory " + dir, failure);
            return List.of();
        }
    }

    private Loaded readFiles(List<Path> files) {
        Map<String, CommandOverride> result = new HashMap<>();
        boolean[] guiDefault = {GUI_DEFAULT};
        files.forEach(file -> readFile(file, result, guiDefault));
        return new Loaded(result, guiDefault[0]);
    }

    private boolean migrateSingleLegacyFile(Path dir, Path rootFile) {
        Path legacyFile = dir.resolve(COMMANDS_FILE);
        List<Path> configFiles = legacyConfFiles(dir);
        if (configFiles.size() != 1 || !configFiles.get(0).equals(legacyFile)) {
            return false;
        }
        try {
            movePreferAtomic(legacyFile, rootFile);
            tryDeleteEmpty(dir);
            log.info("migrated command catalog from {} to {}", legacyFile, rootFile);
            return true;
        } catch (IOException failure) {
            log.error("failed to migrate command catalog from " + legacyFile + " to " + rootFile, failure);
            return false;
        }
    }

    private static void movePreferAtomic(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target);
        }
    }

    private static void tryDeleteEmpty(Path dir) {
        try {
            Files.delete(dir);
        } catch (IOException ignored) {
            // A non-empty legacy directory may contain an operator's unrelated files; retain it untouched.
        }
    }

    private static boolean removeEmptyLegacyDirectory(Path dir) {
        try (Stream<Path> children = Files.list(dir)) {
            if (children.findAny().isPresent()) {
                return false;
            }
        } catch (IOException ignored) {
            return false;
        }
        tryDeleteEmpty(dir);
        return !Files.exists(dir);
    }

    /**
     * Say something only when there is a real split-layout catalog being ignored. The directory holding a
     * {@code custom/} subfolder and nothing else is the normal shape of a fresh install, not a conflict.
     */
    private void warnIfLegacyAlsoExists(Path dir, Path rootFile) {
        if (Files.isDirectory(dir) && !legacyConfFiles(dir).isEmpty()) {
            log.warn("ignoring legacy command catalog directory {} because {} is authoritative", dir, rootFile);
        }
    }

    private static boolean isConfFile(Path file) {
        return Files.isRegularFile(file) && file.getFileName().toString().endsWith(CONF_SUFFIX);
    }

    private void readFile(Path file, Map<String, CommandOverride> into, boolean[] guiDefault) {
        ConfigurationNode root;
        try {
            root = HoconConfigurationLoader.builder().path(file).build().load();
        } catch (ConfigurateException failure) {
            log.error("failed to load command overrides " + file, failure);
            return;
        }
        ConfigurationNode guiDefaultNode = root.node(GUI_DEFAULT_KEY);
        if (!guiDefaultNode.virtual()) {
            // Files merge in name order, so a later file's gui-default wins, mirroring the override merge.
            guiDefault[0] = guiDefaultNode.getBoolean(GUI_DEFAULT);
        }
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                root.node("commands").childrenMap().entrySet()) {
            into.put(String.valueOf(entry.getKey()), parseOverride(entry.getValue()));
        }
    }

    private static CommandOverride parseOverride(ConfigurationNode node) {
        boolean enabled = node.node("enabled").getBoolean(true);
        String rawName = node.node("name").getString();
        Optional<String> name = (rawName == null || rawName.isBlank()) ? Optional.empty() : Optional.of(rawName);
        ConfigurationNode guiNode = node.node("gui");
        Optional<Boolean> gui = guiNode.virtual() ? Optional.empty() : Optional.of(guiNode.getBoolean());
        return new CommandOverride(
                enabled,
                name,
                parseAliases(node.node("aliases")),
                gui,
                parseLocalizedAliases(node.node("localized-aliases")));
    }

    private static List<String> parseAliases(ConfigurationNode node) {
        if (!node.isList()) {
            return List.of();
        }
        List<String> aliases = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            if (value != null) {
                aliases.add(value);
            }
        }
        return List.copyOf(aliases);
    }

    private static Map<String, List<String>> parseLocalizedAliases(ConfigurationNode node) {
        if (!node.isMap()) {
            return Map.of();
        }
        Map<String, List<String>> localized = new LinkedHashMap<>();
        node.childrenMap().forEach((locale, aliases) -> localized.put(String.valueOf(locale), parseAliases(aliases)));
        return Collections.unmodifiableMap(localized);
    }
}
