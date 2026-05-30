package com.uxplima.uxmessentials.shared.adapter.outbound.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The {@link ConfigStore} backed by a Configurate HOCON file. The parsed tree is loaded once on
 * construction and held in an {@link AtomicReference}; {@link #reload()} re-reads the file and swaps
 * the reference, so a reader either sees the whole previous tree or the whole new one — never a
 * half-applied config (CLAUDE.md "swapped atomically via AtomicReference on reload").
 *
 * <p>Dotted HOCON paths ({@code modules.homes.enabled}) are navigated by splitting on {@code .} and
 * descending through {@link ConfigurationNode#node(Object...)}; an absent or virtual node yields the
 * caller's fallback. The kernel only ever sees the {@link ConfigStore} contract — Configurate types
 * stay behind this adapter.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. The current tree lives in an {@link AtomicReference} swapped
 * whole on reload; reads are lock-free against the snapshot they fetch.
 */
@NullMarked
public final class ConfigurateConfigStore implements ConfigStore {

    private final Path file;
    private final Logger log;
    private final HoconConfigurationLoader loader;
    private final AtomicReference<ConfigurationNode> tree;

    private ConfigurateConfigStore(Path file, Logger log, HoconConfigurationLoader loader, ConfigurationNode root) {
        this.file = file;
        this.log = log;
        this.loader = loader;
        this.tree = new AtomicReference<>(root);
    }

    /** Load {@code file} once; an absent file yields an empty tree so every read returns its fallback. */
    public static ConfigurateConfigStore load(Path file, Logger log) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(log, "log");
        HoconConfigurationLoader loader =
                HoconConfigurationLoader.builder().path(file).build();
        ConfigurationNode root = read(loader, file, log);
        return new ConfigurateConfigStore(file, log, loader, root);
    }

    @Override
    public boolean getBoolean(String path, boolean fallback) {
        return at(path).getBoolean(fallback);
    }

    @Override
    public String getString(String path, String fallback) {
        return at(path).getString(fallback);
    }

    @Override
    public int getInt(String path, int fallback) {
        return at(path).getInt(fallback);
    }

    @Override
    public long getLong(String path, long fallback) {
        return at(path).getLong(fallback);
    }

    @Override
    public double getDouble(String path, double fallback) {
        return at(path).getDouble(fallback);
    }

    @Override
    public List<String> getStringList(String path, List<String> fallback) {
        Objects.requireNonNull(fallback, "fallback");
        ConfigurationNode node = at(path);
        if (node.virtual() || !node.isList()) {
            return List.copyOf(fallback);
        }
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            if (value != null) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    @Override
    public void reload() {
        tree.set(read(loader, file, log));
    }

    private ConfigurationNode at(String path) {
        Objects.requireNonNull(path, "path");
        Object[] segments = path.split("\\.");
        ConfigurationNode root = Objects.requireNonNull(tree.get(), "tree");
        return root.node(segments);
    }

    private static ConfigurationNode read(HoconConfigurationLoader loader, Path file, Logger log) {
        if (!Files.exists(file)) {
            return CommentedConfigurationNode.root();
        }
        try {
            return loader.load();
        } catch (ConfigurateException failure) {
            log.error("failed to load config " + file + "; keeping defaults", failure);
            return CommentedConfigurationNode.root();
        }
    }
}
