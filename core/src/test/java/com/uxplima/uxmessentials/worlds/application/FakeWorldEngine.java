package com.uxplima.uxmessentials.worlds.application;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.Nullable;

final class FakeWorldEngine implements WorldEngine {
    final Set<String> loaded = new HashSet<>();
    final Set<String> onDisk = new HashSet<>();
    WorldName defaultWorld = WorldName.of("world");
    int playerCount;
    Optional<DetectedWorld> scanResult = Optional.empty();

    @Nullable ManagedWorld lastLoaded;

    @Override
    public Result<Unit, WorldError> create(ManagedWorld world) {
        loaded.add(world.name().value());
        onDisk.add(world.name().value());
        return Result.ok();
    }

    @Override
    public Result<Unit, WorldError> load(ManagedWorld world) {
        lastLoaded = world;
        loaded.add(world.name().value());
        return Result.ok();
    }

    @Override
    public Result<Unit, WorldError> unload(WorldName name, boolean save) {
        loaded.remove(name.value());
        return Result.ok();
    }

    @Override
    public Result<Unit, WorldError> deleteFiles(WorldName name) {
        onDisk.remove(name.value());
        return Result.ok();
    }

    @Override
    public Optional<DetectedWorld> scanFolder(WorldName name) {
        return scanResult;
    }

    @Override
    public boolean exists(WorldName name) {
        return onDisk.contains(name.value());
    }

    @Override
    public boolean isLoaded(WorldName name) {
        return loaded.contains(name.value());
    }

    @Override
    public Set<WorldName> loadedWorldNames() {
        Set<WorldName> s = new HashSet<>();
        loaded.forEach(n -> s.add(WorldName.of(n)));
        return s;
    }

    @Override
    public Optional<WorldName> defaultWorldName() {
        return Optional.ofNullable(defaultWorld);
    }

    @Override
    public Optional<UUID> uidOf(WorldName name) {
        return loaded.contains(name.value()) ? Optional.of(UUID.randomUUID()) : Optional.empty();
    }

    @Override
    public int playerCount(WorldName name) {
        return playerCount;
    }
}
