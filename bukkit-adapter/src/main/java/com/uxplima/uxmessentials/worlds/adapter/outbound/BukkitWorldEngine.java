package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.domain.BuiltInGenerators;
import com.uxplima.uxmessentials.worlds.domain.DimensionKey;
import com.uxplima.uxmessentials.worlds.domain.DimensionNames;
import com.uxplima.uxmessentials.worlds.domain.GeneratorRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldGenType;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Anti-corruption layer over Bukkit's world APIs. World-handle operations ({@link #create},
 * {@link #load}, {@link #unload}) must be invoked on the global region thread (the caller's
 * responsibility, via the {@code Scheduler} port). Filesystem operations ({@link #deleteFiles},
 * {@link #scanFolder}) read/delete under the server's world container.
 */
@NullMarked
public final class BukkitWorldEngine implements WorldEngine {

    private final Server server;
    private final WorldFolders folders;
    private final Logger log;
    private final WorldGeneratorResolver generators;

    public BukkitWorldEngine(Server server, Logger log, WorldGeneratorResolver generators) {
        this.server = Objects.requireNonNull(server, "server");
        this.folders = new WorldFolders(server);
        this.log = Objects.requireNonNull(log, "log");
        this.generators = Objects.requireNonNull(generators, "generators");
    }

    @Override
    public Result<Unit, WorldError> create(ManagedWorld world) {
        return loadOrCreate(world.name(), Optional.of(world.spec()));
    }

    @Override
    public Result<Unit, WorldError> load(ManagedWorld world) {
        return loadOrCreate(world.name(), Optional.of(world.spec()));
    }

    private Result<Unit, WorldError> loadOrCreate(WorldName name, Optional<WorldSpec> spec) {
        try {
            // A dimension the spec names wins; otherwise the folder decides, so a datapack's dimension loads from
            // its own namespace rather than an empty one being made under minecraft. Paper takes a name or a key,
            // not both, and names a key's world itself the way DimensionNames does.
            NamespacedKey key = spec.flatMap(WorldSpec::dimension)
                    .map(d -> new NamespacedKey(DimensionNames.namespace(d), DimensionNames.path(d)))
                    .orElseGet(() -> folders.keyOf(name.value()));
            WorldCreator creator = key.getNamespace().equals(NamespacedKey.MINECRAFT)
                    ? new WorldCreator(name.value())
                    : WorldCreator.ofKey(key);
            spec.ifPresent(s -> applySpec(creator, s));
            World created = creator.createWorld();
            if (created == null) {
                return Result.err(WorldError.IO_ERROR);
            }
            return Result.ok();
        } catch (RuntimeException e) {
            log.error("Failed to create/load world " + name.value(), e);
            return Result.err(WorldError.IO_ERROR);
        }
    }

    private void applySpec(WorldCreator creator, WorldSpec spec) {
        creator.environment(toBukkitEnvironment(spec.environment()));
        creator.type(toBukkitType(spec.worldType()));
        spec.seed().ifPresent(creator::seed);
        spec.generator().ifPresent(g -> applyGenerator(creator, g));
        creator.generateStructures(spec.generateStructures());
    }

    /**
     * Routes a generator ref onto the {@link WorldCreator}: our own {@code uxmEssentials:void|flat} refs
     * take the object overload (the resolver's {@code ChunkGenerator}), so they behave identically across
     * every path that supplies the spec: an internal {@link #create}, a {@link #load} of a registered
     * world (the spec is re-applied), and a world configured in {@code bukkit.yml} and served through the
     * plugin's {@code getDefaultWorldGenerator} hook. Any other token, an unknown built-in id or an external
     * {@code plugin[:args]} ref: takes Bukkit's String overload unchanged.
     */
    void applyGenerator(WorldCreator creator, GeneratorRef g) {
        BuiltInGenerators.idOf(g.value())
                .ifPresentOrElse(
                        id -> generators
                                .resolve(id)
                                .ifPresentOrElse(creator::generator, () -> creator.generator(g.value())),
                        () -> creator.generator(g.value()));
    }

    @Override
    public Result<Unit, WorldError> unload(WorldName name, boolean save) {
        World world = server.getWorld(name.value());
        if (world == null) {
            return Result.err(WorldError.NOT_LOADED);
        }
        return server.unloadWorld(world, save) ? Result.ok() : Result.err(WorldError.IO_ERROR);
    }

    @Override
    public Result<Unit, WorldError> deleteFiles(WorldName name) {
        if (server.getWorld(name.value()) != null) {
            return Result.err(WorldError.ALREADY_LOADED); // must be unloaded first
        }
        Path folder = worldFolder(name);
        if (!Files.isDirectory(folder)) {
            return Result.err(WorldError.FOLDER_MISSING);
        }
        try (var paths = Files.walk(folder)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
            return Files.exists(folder) ? Result.err(WorldError.IO_ERROR) : Result.ok();
        } catch (IOException e) {
            log.error("Failed to delete world folder " + name.value(), e);
            return Result.err(WorldError.IO_ERROR);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("Failed to delete " + path, e);
        }
    }

    @Override
    public Optional<DetectedWorld> scanFolder(WorldName name) {
        World live = server.getWorld(name.value());
        if (live != null) {
            return Optional.of(
                    new DetectedWorld(fromBukkitEnvironment(live.getEnvironment()), Optional.of(live.getSeed())));
        }
        // A dimension folder of Paper 26.2 holds no level.dat of its own, so the folder being there is the test.
        if (!folders.exists(name.value())) {
            return Optional.empty();
        }
        // The seed lives in the level's level.dat and needs NMS; the environment follows the dimension's key.
        return Optional.of(new DetectedWorld(
                DimensionNames.environment(folders.keyOf(name.value()).value()), Optional.empty()));
    }

    @Override
    public Optional<DetectedWorld> scanFolder(WorldName name, Optional<DimensionKey> dimension) {
        if (dimension.isEmpty()) {
            return scanFolder(name);
        }
        if (server.getWorld(name.value()) != null || !folders.exists(name.value(), dimension)) {
            return scanFolder(name);
        }
        return Optional.of(
                new DetectedWorld(DimensionNames.environment(DimensionNames.path(dimension.get())), Optional.empty()));
    }

    @Override
    public boolean exists(WorldName name, Optional<DimensionKey> dimension) {
        return folders.exists(name.value(), dimension);
    }

    /**
     * The world name the server gives {@code raw} when it is a {@code namespace:path} key or the bare path of one of
     * the level's three dimensions ({@code the_nether} reaches {@code world_nether}), else {@code raw}.
     */
    public String worldNameOf(String raw) {
        String levelName = String.valueOf(server.getLevelDirectory().getFileName());
        Optional<DimensionKey> typed = DimensionNames.parse(raw);
        if (typed.isPresent()) {
            return DimensionNames.worldName(typed.get(), levelName);
        }
        return switch (raw) {
            case "overworld", "the_nether", "the_end" ->
                DimensionNames.worldName(DimensionKey.of(NamespacedKey.MINECRAFT + ":" + raw), levelName);
            default -> raw;
        };
    }

    @Override
    public boolean exists(WorldName name) {
        return folders.exists(name.value());
    }

    @Override
    public boolean isLoaded(WorldName name) {
        return server.getWorld(name.value()) != null;
    }

    @Override
    public Set<WorldName> loadedWorldNames() {
        Set<WorldName> names = new HashSet<>();
        for (World world : server.getWorlds()) {
            names.add(WorldName.of(world.getName()));
        }
        return names;
    }

    /**
     * Every world with files on disk, loaded or not: each dimension of the level, and each folder beside the server
     * that holds a {@code level.dat}, which is {@link WorldFolders#onDisk()}. Only the second was scanned before
     * Paper 26.2 moved every world under the level, so the reconcile saw no world at all.
     * Used by the enable-time reconcile in sub-project A; not part of the {@link WorldEngine} port.
     */
    public Set<WorldName> onDiskWorldNames() {
        Set<WorldName> names = new HashSet<>();
        try {
            folders.onDisk().forEach(name -> addIfValid(names, name));
        } catch (UncheckedIOException e) {
            log.error("Failed to scan the world folders", e);
        }
        return names;
    }

    private void addIfValid(Set<WorldName> names, String name) {
        try {
            names.add(WorldName.of(name));
        } catch (IllegalArgumentException e) {
            log.warn("Skipping world folder with an unusable name: {}", name);
        }
    }

    @Override
    public Optional<WorldName> defaultWorldName() {
        List<World> worlds = server.getWorlds();
        return worlds.isEmpty()
                ? Optional.empty()
                : Optional.of(WorldName.of(worlds.get(0).getName()));
    }

    @Override
    public Optional<UUID> uidOf(WorldName name) {
        World world = server.getWorld(name.value());
        return Optional.ofNullable(world).map(World::getUID);
    }

    @Override
    public int playerCount(WorldName name) {
        World world = server.getWorld(name.value());
        return world == null ? 0 : world.getPlayers().size();
    }

    @Override
    public Optional<Position> spawnPoint(WorldName name) {
        World world = server.getWorld(name.value());
        return Optional.ofNullable(world).map(w -> BukkitRefs.toPosition(w.getSpawnLocation()));
    }

    /** Where the world keeps its files, which on Paper 26.2 is under the level's dimensions: {@link WorldFolders}. */
    private Path worldFolder(WorldName name) {
        return folders.of(name.value());
    }

    private static World.Environment toBukkitEnvironment(WorldEnvironment environment) {
        return switch (environment) {
            case NORMAL -> World.Environment.NORMAL;
            case NETHER -> World.Environment.NETHER;
            case THE_END -> World.Environment.THE_END;
        };
    }

    private static WorldEnvironment fromBukkitEnvironment(World.@Nullable Environment environment) {
        if (environment == null) {
            return WorldEnvironment.NORMAL;
        }
        return switch (environment) {
            case NETHER -> WorldEnvironment.NETHER;
            case THE_END -> WorldEnvironment.THE_END;
            default -> WorldEnvironment.NORMAL;
        };
    }

    private static WorldType toBukkitType(WorldGenType type) {
        return switch (type) {
            case NORMAL -> WorldType.NORMAL;
            case FLAT -> WorldType.FLAT;
            case AMPLIFIED -> WorldType.AMPLIFIED;
            case LARGE_BIOMES -> WorldType.LARGE_BIOMES;
        };
    }
}
