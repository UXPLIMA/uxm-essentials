package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.bukkit.Server;
import org.bukkit.World;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;
import org.jspecify.annotations.NullMarked;

/**
 * Wraps the durable {@link SpawnDirectory} (the jOOQ store) with the one piece of knowledge only a live server
 * holds: a world's vanilla spawn. Every read and write delegates to the durable store; the single override is
 * {@link #defaultSpawn(WorldRef)}, the bottom of the resolution chain, which folds in the vanilla world spawn
 * when no operator spawn has been set so {@code /spawn} still answers on a fresh server before any
 * {@code /setspawn} or {@code /setmainspawn}.
 *
 * <p>Keeping the {@code org.bukkit.World} read here is what lets the persistence module stay free of Bukkit:
 * the durable store reports an empty {@code defaultSpawn} for an unset world, and this decorator supplies the
 * platform last-resort. The vanilla read touches the loaded world only and is safe from any thread.
 */
@NullMarked
public final class VanillaFallbackSpawnDirectory implements SpawnDirectory {

    private final SpawnDirectory durable;
    private final Function<WorldRef, Optional<Position>> vanillaSpawn;

    public VanillaFallbackSpawnDirectory(SpawnDirectory durable, Server server) {
        this.durable = Objects.requireNonNull(durable, "durable");
        Objects.requireNonNull(server, "server");
        this.vanillaSpawn = world -> {
            World live = server.getWorld(world.uid());
            return live == null ? Optional.empty() : Optional.of(BukkitRefs.toPosition(live.getSpawnLocation()));
        };
    }

    @Override
    public Optional<Position> defaultSpawn(WorldRef world) {
        Objects.requireNonNull(world, "world");
        return durable.operatorSpawn(world).or(() -> vanillaSpawn.apply(world));
    }

    @Override
    public Optional<Position> operatorSpawn(WorldRef world) {
        return durable.operatorSpawn(world);
    }

    @Override
    public Optional<Position> mainSpawn() {
        return durable.mainSpawn();
    }

    @Override
    public Optional<Position> namedSpawn(String name) {
        return durable.namedSpawn(name);
    }

    @Override
    public Optional<SpawnMirror> mirrorFor(WorldRef world) {
        return durable.mirrorFor(world);
    }

    @Override
    public void setDefaultSpawn(WorldRef world, Position position) {
        durable.setDefaultSpawn(world, position);
    }

    @Override
    public void setNamedSpawn(String name, Position position) {
        durable.setNamedSpawn(name, position);
    }

    @Override
    public void setMainSpawn(Position position) {
        durable.setMainSpawn(position);
    }

    @Override
    public boolean removeDefaultSpawn(WorldRef world) {
        return durable.removeDefaultSpawn(world);
    }

    @Override
    public void setMirror(SpawnMirror mirror) {
        durable.setMirror(mirror);
    }
}
