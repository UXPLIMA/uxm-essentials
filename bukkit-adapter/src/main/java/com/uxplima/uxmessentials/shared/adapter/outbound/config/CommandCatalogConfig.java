package com.uxplima.uxmessentials.shared.adapter.outbound.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
 * Reads the {@code commands/<module>.conf} files into the override map the
 * {@link com.uxplima.uxmessentials.shared.application.command.CommandCatalog} resolves against. Each file
 * holds a {@code commands { <id> { ... } }} block; ids are taken as raw HOCON map keys and merged across
 * files, so the per-module split is purely an operator convenience.
 *
 * <p>Files are read in name order for a deterministic merge, an absent {@code commands} directory yields
 * an empty map (so an untouched install simply uses code defaults), and a single unparseable file is
 * logged and skipped rather than failing the whole load — one bad edit must not silence every command's
 * overrides. Validating the id shape is the resolver's job, so the map is keyed by the raw string.
 */
@NullMarked
public final class CommandCatalogConfig {

    private static final String COMMANDS_DIR = "commands";
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

    /** Merge every {@code commands/*.conf} override into a single map keyed by command id. */
    public Map<String, CommandOverride> load() {
        return loadAll().overrides();
    }

    /** Merge every {@code commands/*.conf} file into the override map plus the global {@code gui-default}. */
    public Loaded loadAll() {
        Path dir = dataFolder.resolve(COMMANDS_DIR);
        if (!Files.isDirectory(dir)) {
            return new Loaded(Map.of(), GUI_DEFAULT);
        }
        Map<String, CommandOverride> result = new HashMap<>();
        boolean[] guiDefault = {GUI_DEFAULT};
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(CommandCatalogConfig::isConfFile)
                    .sorted(Comparator.comparing(Path::getFileName))
                    .forEach(file -> readFile(file, result, guiDefault));
        } catch (IOException failure) {
            log.error("failed to list command override directory " + dir, failure);
        }
        return new Loaded(result, guiDefault[0]);
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
        return new CommandOverride(enabled, name, parseAliases(node.node("aliases")), gui);
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
}
