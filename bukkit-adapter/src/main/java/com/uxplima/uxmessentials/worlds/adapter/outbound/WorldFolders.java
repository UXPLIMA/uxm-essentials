package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.bukkit.Server;
import org.bukkit.World;

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

    private static final String DEFAULT_LEVEL = "world";

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
        for (Path dimension : folders(level().resolve("dimensions").resolve("minecraft"))) {
            names.add(nameOf(String.valueOf(dimension.getFileName())));
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

    private Path dimension(String name) {
        return level().resolve("dimensions").resolve("minecraft").resolve(keyOf(name));
    }

    /** The key the server gives a world of that name: the level's own three by their vanilla keys, others lower-cased. */
    private String keyOf(String name) {
        String levelName = levelName();
        if (name.equals(levelName)) {
            return "overworld";
        }
        if (name.equals(levelName + NETHER_SUFFIX)) {
            return "the_nether";
        }
        if (name.equals(levelName + END_SUFFIX)) {
            return "the_end";
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private String nameOf(String key) {
        return switch (key) {
            case "overworld" -> levelName();
            case "the_nether" -> levelName() + NETHER_SUFFIX;
            case "the_end" -> levelName() + END_SUFFIX;
            default -> key;
        };
    }

    private Path level() {
        return container().resolve(levelName());
    }

    private String levelName() {
        List<World> worlds = server.getWorlds();
        return worlds.isEmpty() ? DEFAULT_LEVEL : worlds.get(0).getName();
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
