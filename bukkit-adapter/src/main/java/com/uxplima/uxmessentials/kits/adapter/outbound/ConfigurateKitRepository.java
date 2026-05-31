package com.uxplima.uxmessentials.kits.adapter.outbound;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The {@link KitRepository} backed by {@code kits.conf} (Configurate HOCON). Kit definitions are loaded once
 * on construction into an immutable id-keyed map held in an {@link AtomicReference}, so reads (the hot
 * {@code /kit} / {@code /kits} paths) are lock-free against the snapshot they fetch. The rare authoring
 * operations ({@code /createkit}, {@code /delkit}, {@code /kiteditor}) rebuild the map, swap the reference,
 * and write the whole tree back to disk, so the in-memory catalog and the file never drift.
 *
 * <p>Parsing each entry is delegated to {@link KitCodec}; a malformed entry is logged and skipped at load so
 * one bad kit never strands the rest. The file write is serialized by {@code synchronized} on this
 * repository — authoring is single-operator and rare, so the coarse lock is fine and keeps the save atomic
 * against a concurrent edit.
 */
@NullMarked
public final class ConfigurateKitRepository implements KitRepository {

    private final Path file;
    private final Logger log;
    private final HoconConfigurationLoader loader;
    private final AtomicReference<Map<String, KitDefinition>> kits;

    private ConfigurateKitRepository(
            Path file, Logger log, HoconConfigurationLoader loader, Map<String, KitDefinition> initial) {
        this.file = file;
        this.log = log;
        this.loader = loader;
        this.kits = new AtomicReference<>(Map.copyOf(initial));
    }

    /** Load {@code file} once; an absent or unreadable file yields an empty catalog. */
    public static ConfigurateKitRepository load(Path file, Logger log) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(log, "log");
        HoconConfigurationLoader loader =
                HoconConfigurationLoader.builder().path(file).build();
        return new ConfigurateKitRepository(file, log, loader, read(loader, file, log));
    }

    @Override
    public Optional<KitDefinition> find(KitId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(current().get(id.value()));
    }

    @Override
    public List<KitDefinition> all() {
        return List.copyOf(current().values());
    }

    @Override
    public boolean exists(KitId id) {
        Objects.requireNonNull(id, "id");
        return current().containsKey(id.value());
    }

    @Override
    public synchronized void save(KitDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        Map<String, KitDefinition> next = new LinkedHashMap<>(current());
        next.put(definition.id().value(), definition);
        persist(next);
    }

    @Override
    public synchronized void delete(KitId id) {
        Objects.requireNonNull(id, "id");
        Map<String, KitDefinition> next = new LinkedHashMap<>(current());
        if (next.remove(id.value()) != null) {
            persist(next);
        }
    }

    private Map<String, KitDefinition> current() {
        return Objects.requireNonNull(kits.get(), "kits");
    }

    private void persist(Map<String, KitDefinition> next) {
        try {
            CommentedConfigurationNode root = CommentedConfigurationNode.root();
            ConfigurationNode kitsNode = root.node("kits");
            for (KitDefinition definition : next.values()) {
                KitCodec.write(kitsNode.node(definition.id().value()), definition);
            }
            loader.save(root);
            kits.set(Map.copyOf(next));
        } catch (ConfigurateException failure) {
            log.error("failed to save kits to " + file, failure);
        }
    }

    private static Map<String, KitDefinition> read(HoconConfigurationLoader loader, Path file, Logger log) {
        Map<String, KitDefinition> loaded = new LinkedHashMap<>();
        ConfigurationNode kitsNode = rootKitsNode(loader, file, log);
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                kitsNode.childrenMap().entrySet()) {
            String id = String.valueOf(entry.getKey());
            KitCodec.read(id, entry.getValue())
                    .ifPresentOrElse(
                            kit -> loaded.put(kit.id().value(), kit),
                            () -> log.warn("skipping malformed kit entry: " + id));
        }
        return loaded;
    }

    private static ConfigurationNode rootKitsNode(HoconConfigurationLoader loader, Path file, Logger log) {
        if (!java.nio.file.Files.exists(file)) {
            return CommentedConfigurationNode.root();
        }
        try {
            return loader.load().node("kits");
        } catch (ConfigurateException failure) {
            log.error("failed to load kits from " + file + "; starting with an empty catalog", failure);
            return CommentedConfigurationNode.root();
        }
    }
}
