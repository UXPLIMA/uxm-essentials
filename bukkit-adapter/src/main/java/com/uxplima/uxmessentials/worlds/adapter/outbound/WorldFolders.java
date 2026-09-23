package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;

/**
 * Where a world keeps its files.
 *
 * <p>On Paper 26.2 a world is not a folder beside the server jar. The level named in {@code server.properties} holds
 * one {@code level.dat}, and every world of it is a dimension under {@code <level>/dimensions/<namespace>/<key>}: the
 * overworld at {@code world/dimensions/minecraft/overworld}, a world made as {@code probeworld} at
 * {@code world/dimensions/minecraft/probeworld}. A loaded world says where it is. For one that is not loaded, the
 * dimension folder is tried first, then a folder beside the server the way a world used to be copied in, and a world
 * that is in neither place is answered with where a new one would go.
 */
public final class WorldFolders {

    private static final String NETHER_SUFFIX = "_nether";

    private static final String END_SUFFIX = "_the_end";

    private final Server server;

    public WorldFolders(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    /**
     * The folder the world named {@code name} keeps its files in, or would.
     *
     * @throws IllegalArgumentException if {@code name} is a path rather than a name
     */
    public Path of(String name) {
        Objects.requireNonNull(name, "name");
        if (!isAName(name)) {
            throw new IllegalArgumentException("a world name is not a path: " + name);
        }
        World live = server.getWorld(name);
        if (live != null) {
            return live.getWorldFolder().toPath();
        }
        Path dimension = dimension(name);
        if (Files.isDirectory(dimension)) {
            return dimension;
        }
        Path beside = container().resolve(name);
        return Files.isDirectory(beside) ? beside : dimension;
    }

    /** Whether the world named {@code name} is loaded or has a folder in either place. */
    public boolean exists(String name) {
        Objects.requireNonNull(name, "name");
        if (!isAName(name)) {
            return false;
        }
        return server.getWorld(name) != null
                || Files.isDirectory(dimension(name))
                || Files.isDirectory(container().resolve(name));
    }

    /**
     * The name of every world with files on disk, loaded or not, the way the server names it: the level's own three
     * dimensions as {@code world}, {@code world_nether} and {@code world_the_end}, any other dimension by its key,
     * and every folder beside the server that holds a {@code level.dat}.
     */
    public Set<String> onDisk() {
        Set<String> names = new TreeSet<>();
        for (Path namespace : folders(level().resolve("dimensions"))) {
            for (Path dimension : folders(namespace)) {
                names.add(nameOf(String.valueOf(dimension.getFileName())));
            }
        }
        for (Path beside : folders(container())) {
            if (Files.isRegularFile(beside.resolve("level.dat")) && !beside.equals(level())) {
                names.add(String.valueOf(beside.getFileName()));
            }
        }
        return names;
    }

    /** A name and not a path: a folder name must never climb out of the level or the container. */
    private static boolean isAName(String name) {
        return !name.isBlank()
                && !name.equals(".")
                && !name.equals("..")
                && name.indexOf('/') < 0
                && name.indexOf('\\') < 0;
    }

    /**
     * The dimension folder of a world of that name, under the key the server gives it. {@link WorldCreator#key()} is
     * Paper's own rule: the level's three become {@code overworld}, {@code the_nether} and {@code the_end}, others
     * are lower-cased with a space turned into an underscore. uxm-plots pointed at it, and a hand-written copy of the
     * rule got the space wrong.
     */
    private Path dimension(String name) {
        NamespacedKey key = new WorldCreator(name).key();
        return level().resolve("dimensions").resolve(key.namespace()).resolve(key.value());
    }

    private String nameOf(String key) {
        String levelName = String.valueOf(level().getFileName());
        return switch (key) {
            case "overworld" -> levelName;
            case "the_nether" -> levelName + NETHER_SUFFIX;
            case "the_end" -> levelName + END_SUFFIX;
            default -> key;
        };
    }

    private Path level() {
        return server.getLevelDirectory();
    }

    private Path container() {
        return server.getWorldContainer().toPath();
    }

    private static List<Path> folders(Path parent) {
        if (!Files.isDirectory(parent)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(parent)) {
            return entries.filter(Files::isDirectory).toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException("cannot list " + parent, unreadable);
        }
    }
}
