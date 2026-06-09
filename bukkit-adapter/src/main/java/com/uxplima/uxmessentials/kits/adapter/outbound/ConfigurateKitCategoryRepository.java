package com.uxplima.uxmessentials.kits.adapter.outbound;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Persists and loads kit categories from <data-dir>/modules/kits/categories/*.conf HOCON files.
 */
@NullMarked
public final class ConfigurateKitCategoryRepository implements KitCategoryRepository {

    private static final String SUFFIX = ".conf";

    private final Path dir;
    private final Logger log;
    private final AtomicReference<Map<String, KitCategory>> categories;

    private ConfigurateKitCategoryRepository(Path dir, Logger log, Map<String, KitCategory> initial) {
        this.dir = dir;
        this.log = log;
        this.categories = new AtomicReference<>(Map.copyOf(initial));
    }

    public static ConfigurateKitCategoryRepository load(Path dir, Logger log) {
        Objects.requireNonNull(dir, "dir");
        Objects.requireNonNull(log, "log");
        return new ConfigurateKitCategoryRepository(dir, log, readAll(dir, log));
    }

    @Override
    public Optional<KitCategory> find(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(current().get(id));
    }

    @Override
    public List<KitCategory> all() {
        return List.copyOf(current().values());
    }

    @Override
    public synchronized void save(KitCategory category) {
        Objects.requireNonNull(category, "category");
        try {
            writeCategoryFile(category);
        } catch (IOException failure) {
            log.error("failed to save category " + category.id() + " under " + dir, failure);
            return;
        }
        Map<String, KitCategory> next = new LinkedHashMap<>(current());
        next.put(category.id(), category);
        categories.set(Map.copyOf(next));
    }

    @Override
    public synchronized void delete(String id) {
        Objects.requireNonNull(id, "id");
        try {
            Files.deleteIfExists(fileFor(id));
        } catch (IOException failure) {
            log.error("failed to delete category " + id + " under " + dir, failure);
            return;
        }
        Map<String, KitCategory> next = new LinkedHashMap<>(current());
        if (next.remove(id) != null) {
            categories.set(Map.copyOf(next));
        }
    }

    private Map<String, KitCategory> current() {
        return Objects.requireNonNull(categories.get(), "categories");
    }

    private Path fileFor(String id) {
        return dir.resolve(id + SUFFIX);
    }

    private void writeCategoryFile(KitCategory category) throws IOException {
        Files.createDirectories(dir);
        Path target = fileFor(category.id());
        Path temp = dir.resolve(category.id() + SUFFIX + ".tmp");
        CommentedConfigurationNode root = CommentedConfigurationNode.root();

        root.node("display-name").set(category.displayName());
        if (category.displayMaterial().isPresent()) {
            root.node("display-material").set(category.displayMaterial().get());
        }
        if (!category.displayLore().isEmpty()) {
            root.node("display-lore").set(category.displayLore());
        }
        root.node("slot").set(category.slot());
        if (category.parentCategoryId().isPresent()) {
            root.node("parent-category-id").set(category.parentCategoryId().get());
        }

        HoconConfigurationLoader.builder().path(temp).build().save(root);
        atomicMove(temp, target);
    }

    private static void atomicMove(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException atomicUnsupported) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Map<String, KitCategory> readAll(Path dir, Logger log) {
        Map<String, KitCategory> loaded = new LinkedHashMap<>();
        if (!Files.isDirectory(dir)) {
            return loaded;
        }
        for (Path file : sortedConfFiles(dir, log)) {
            readFile(file, log).ifPresent(cat -> loaded.put(cat.id(), cat));
        }
        return loaded;
    }

    private static List<Path> sortedConfFiles(Path dir, Logger log) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + SUFFIX)) {
            stream.forEach(files::add);
        } catch (IOException failure) {
            log.error("failed to list category files under " + dir, failure);
            return List.of();
        }
        files.sort(
                (a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
        return files;
    }

    private static Optional<KitCategory> readFile(Path file, Logger log) {
        String name = file.getFileName().toString();
        String id = name.substring(0, name.length() - SUFFIX.length());
        try {
            ConfigurationNode root =
                    HoconConfigurationLoader.builder().path(file).build().load();
            String displayName = root.node("display-name").getString("");
            if (displayName.isEmpty()) {
                displayName = id;
            }
            Optional<String> displayMaterial =
                    Optional.ofNullable(root.node("display-material").getString());
            List<String> displayLore = new ArrayList<>();
            ConfigurationNode loreNode = root.node("display-lore");
            if (!loreNode.virtual() && loreNode.isList()) {
                for (ConfigurationNode child : loreNode.childrenList()) {
                    String line = child.getString();
                    if (line != null) {
                        displayLore.add(line);
                    }
                }
            }
            int slot = root.node("slot").getInt(0);
            Optional<String> parentCategoryId =
                    Optional.ofNullable(root.node("parent-category-id").getString());

            return Optional.of(new KitCategory(id, displayName, displayMaterial, displayLore, slot, parentCategoryId));
        } catch (ConfigurateException failure) {
            log.error("failed to read category file " + file + "; skipping it", failure);
            return Optional.empty();
        }
    }
}
