package com.uxplima.uxmessentials.worlds.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** A server whose worlds are names: folders on disk, some of them loaded, and what each was created as. */
final class FakeWorldEngine implements WorldEngine {

    static final WorldName DEFAULT_WORLD = WorldName.of("world");

    private final Set<WorldName> onDisk = new HashSet<>(Set.of(DEFAULT_WORLD));
    private final Set<WorldName> loaded = new HashSet<>(Set.of(DEFAULT_WORLD));
    private final Map<WorldName, ManagedWorld> created = new LinkedHashMap<>();
    private final Map<WorldName, Position> spawns = new LinkedHashMap<>();
    private final Map<WorldName, UUID> uids = new LinkedHashMap<>();
    private final List<WorldName> loads = new ArrayList<>();
    private final Map<WorldName, Integer> players = new LinkedHashMap<>();
    private boolean refusesLoads;

    /** {@code count} players standing in {@code name}. */
    void players(WorldName name, int count) {
        players.put(name, count);
    }

    /** The server unloads {@code name}, keeping its folder. */
    void unload(WorldName name) {
        loaded.remove(name);
    }

    /** A world folder on disk, loaded or not, with the spawn it has once loaded. */
    void onDisk(WorldName name, boolean isLoaded, Position spawn) {
        onDisk.add(name);
        if (isLoaded) {
            loaded.add(name);
        }
        spawns.put(name, spawn);
        uids.put(name, spawn.world().uid());
    }

    /** Every world this engine was asked to load, in order. */
    List<WorldName> loads() {
        return List.copyOf(loads);
    }

    void refuseLoads(boolean refuses) {
        this.refusesLoads = refuses;
    }

    /** The aggregate the engine was asked to build under {@code name}, as it was handed over. */
    Optional<ManagedWorld> createdAs(WorldName name) {
        return Optional.ofNullable(created.get(name));
    }

    @Override
    public Result<Unit, WorldError> create(ManagedWorld world) {
        created.put(world.name(), world);
        onDisk.add(world.name());
        loaded.add(world.name());
        return Result.ok();
    }

    @Override
    public Result<Unit, WorldError> load(ManagedWorld world) {
        loads.add(world.name());
        if (refusesLoads) {
            return Result.err(WorldError.IO_ERROR);
        }
        loaded.add(world.name());
        return Result.ok();
    }

    @Override
    public Result<Unit, WorldError> unload(WorldName name, boolean save) {
        return loaded.remove(name) ? Result.ok() : Result.err(WorldError.NOT_LOADED);
    }

    @Override
    public Result<Unit, WorldError> deleteFiles(WorldName name) {
        if (loaded.contains(name)) {
            return Result.err(WorldError.ALREADY_LOADED);
        }
        return onDisk.remove(name) ? Result.ok() : Result.err(WorldError.FOLDER_MISSING);
    }

    @Override
    public Optional<DetectedWorld> scanFolder(WorldName name) {
        return Optional.empty();
    }

    @Override
    public boolean exists(WorldName name) {
        return onDisk.contains(name);
    }

    @Override
    public boolean isLoaded(WorldName name) {
        return loaded.contains(name);
    }

    @Override
    public Set<WorldName> loadedWorldNames() {
        return Set.copyOf(loaded);
    }

    @Override
    public Optional<WorldName> defaultWorldName() {
        return Optional.of(DEFAULT_WORLD);
    }

    @Override
    public Optional<UUID> uidOf(WorldName name) {
        return loaded.contains(name) ? Optional.ofNullable(uids.get(name)) : Optional.empty();
    }

    @Override
    public int playerCount(WorldName name) {
        return players.getOrDefault(name, 0);
    }

    @Override
    public Optional<Position> spawnPoint(WorldName name) {
        return loaded.contains(name) ? Optional.ofNullable(spawns.get(name)) : Optional.empty();
    }
}
