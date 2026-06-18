package com.uxplima.uxmessentials.worlds.application.port;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/**
 * Anti-corruption layer over Bukkit's world APIs ({@code WorldCreator}, {@code Server#getWorld},
 * {@code unloadWorld}, the world folder on disk). The only place in the worlds context that touches
 * {@code org.bukkit.World}. World handle operations run on the global thread; file operations run
 * off-tick — both are the adapter's responsibility, invoked through the {@code Scheduler} port.
 */
public interface WorldEngine {

    /** Create and load the world described by the aggregate. */
    Result<Unit, WorldError> create(ManagedWorld world);

    /** Load an existing (registered or on-disk) world. */
    Result<Unit, WorldError> load(WorldName name);

    /** Unload a loaded world, optionally saving it first. */
    Result<Unit, WorldError> unload(WorldName name, boolean save);

    /** Permanently delete the world's folder from disk (must be unloaded and non-default). */
    Result<Unit, WorldError> deleteFiles(WorldName name);

    /** Read {@code level.dat} for an on-disk, possibly-unloaded world. */
    Optional<DetectedWorld> scanFolder(WorldName name);

    boolean exists(WorldName name);

    boolean isLoaded(WorldName name);

    Set<WorldName> loadedWorldNames();

    WorldName defaultWorldName();

    Optional<UUID> uidOf(WorldName name);

    int playerCount(WorldName name);

    /** What a folder scan can determine about a world without loading it. */
    record DetectedWorld(WorldEnvironment environment, Optional<Long> seed) {}
}
