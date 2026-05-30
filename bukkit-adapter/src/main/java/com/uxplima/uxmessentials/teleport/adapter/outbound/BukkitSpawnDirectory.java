package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.World;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link SpawnDirectory} implementation. Operator-set default and named spawns are held in-memory and
 * fall back to each world's vanilla spawn when none has been set, so {@code /spawn} works on a fresh
 * server before any {@code /setspawn}. Spawn-mirror rules are loaded once at construction from the
 * teleport config (the source-world → target-world redirects).
 *
 * <p>Durable persistence of operator-set spawns (a {@code spawns} table or a {@code teleport.conf} write)
 * is the persistence-adapter's concern and lands with the DB wiring; until then an operator's
 * {@code /setspawn} survives for the server session only. This is a documented stub, not a silent gap.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. Defaults, named spawns, and mirrors are
 * {@link ConcurrentHashMap}s safe from any thread; reading the live vanilla spawn touches the loaded
 * {@code World} only.
 */
@NullMarked
public final class BukkitSpawnDirectory implements SpawnDirectory {

    private final ConcurrentHashMap<UUID, Position> defaults = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Position> named = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, SpawnMirror> mirrors = new ConcurrentHashMap<>();

    public BukkitSpawnDirectory(java.util.Collection<SpawnMirror> configuredMirrors) {
        Objects.requireNonNull(configuredMirrors, "configuredMirrors");
        for (SpawnMirror mirror : configuredMirrors) {
            mirrors.put(mirror.sourceWorld(), mirror);
        }
    }

    @Override
    public Optional<Position> defaultSpawn(WorldRef world) {
        Objects.requireNonNull(world, "world");
        Position operatorSet = defaults.get(world.uid());
        return operatorSet != null ? Optional.of(operatorSet) : vanillaSpawn(world.uid());
    }

    @Override
    public Optional<Position> namedSpawn(String name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(named.get(key(name)));
    }

    @Override
    public Optional<SpawnMirror> mirrorFor(WorldRef world) {
        Objects.requireNonNull(world, "world");
        return Optional.ofNullable(mirrors.get(world.uid()));
    }

    @Override
    public void setDefaultSpawn(WorldRef world, Position position) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(position, "position");
        defaults.put(world.uid(), position);
    }

    @Override
    public void setNamedSpawn(String name, Position position) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(position, "position");
        named.put(key(name), position);
    }

    private static Optional<Position> vanillaSpawn(UUID worldUid) {
        World world = Bukkit.getWorld(worldUid);
        return world == null ? Optional.empty() : Optional.of(BukkitRefs.toPosition(world.getSpawnLocation()));
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
