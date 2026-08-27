package com.uxplima.uxmessentials.persistence.teleport;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;

/**
 * Write-through spawn cache. The complete durable set is warmed at enable so command, join and respawn region
 * threads never query the database; writes update the durable store first and then publish the new cached value.
 */
public final class CachedSpawnDirectory implements SpawnDirectory {

    private final SpawnDirectory delegate;
    private final Supplier<Optional<SpawnDirectorySnapshot>> snapshotLoader;
    private final ConcurrentHashMap<UUID, Optional<Position>> worlds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Optional<SpawnMirror>> mirrors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Optional<Position>> named = new ConcurrentHashMap<>();
    private final AtomicReference<Optional<Position>> main = new AtomicReference<>();
    private volatile boolean authoritative;

    public CachedSpawnDirectory(SpawnDirectory delegate) {
        this(delegate, Optional::empty);
    }

    CachedSpawnDirectory(SpawnDirectory delegate, Supplier<Optional<SpawnDirectorySnapshot>> snapshotLoader) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.snapshotLoader = Objects.requireNonNull(snapshotLoader, "snapshotLoader");
    }

    /** Load the complete durable set; after this call even absent-key reads are memory-only. */
    public synchronized void warmAll() {
        Optional<SpawnDirectorySnapshot> loaded = snapshotLoader.get();
        if (loaded.isEmpty()) {
            return;
        }
        SpawnDirectorySnapshot snapshot = loaded.get();
        worlds.clear();
        snapshot.worlds().forEach((id, position) -> worlds.put(id, Optional.of(position)));
        named.clear();
        snapshot.named().forEach((name, position) -> named.put(name, Optional.of(position)));
        mirrors.clear();
        snapshot.mirrors().forEach((id, mirror) -> mirrors.put(id, Optional.of(mirror)));
        main.set(snapshot.main());
        authoritative = true;
    }

    /** Warm every spawn fact needed by automatic join and death handling. */
    public void warm(Collection<WorldRef> loadedWorlds) {
        Objects.requireNonNull(loadedWorlds, "loadedWorlds");
        mainSpawn();
        loadedWorlds.forEach(world -> {
            operatorSpawn(world);
            mirrorFor(world);
        });
    }

    @Override
    public Optional<Position> defaultSpawn(WorldRef world) {
        return operatorSpawn(world);
    }

    @Override
    public Optional<Position> operatorSpawn(WorldRef world) {
        Objects.requireNonNull(world, "world");
        Optional<Position> cached = worlds.get(world.uid());
        if (cached != null || authoritative) {
            return cached == null ? Optional.empty() : cached;
        }
        return worlds.computeIfAbsent(world.uid(), ignored -> delegate.operatorSpawn(world));
    }

    @Override
    public Optional<Position> mainSpawn() {
        Optional<Position> current = main.get();
        if (current != null) {
            return current;
        }
        Optional<Position> loaded = delegate.mainSpawn();
        return main.compareAndSet(null, loaded) ? loaded : Objects.requireNonNull(main.get());
    }

    @Override
    public Optional<Position> namedSpawn(String name) {
        Objects.requireNonNull(name, "name");
        String key = key(name);
        Optional<Position> cached = named.get(key);
        if (cached != null || authoritative) {
            return cached == null ? Optional.empty() : cached;
        }
        return named.computeIfAbsent(key, ignored -> delegate.namedSpawn(name));
    }

    @Override
    public Optional<SpawnMirror> mirrorFor(WorldRef world) {
        Objects.requireNonNull(world, "world");
        Optional<SpawnMirror> cached = mirrors.get(world.uid());
        if (cached != null || authoritative) {
            return cached == null ? Optional.empty() : cached;
        }
        return mirrors.computeIfAbsent(world.uid(), ignored -> delegate.mirrorFor(world));
    }

    @Override
    public void setDefaultSpawn(WorldRef world, Position position) {
        delegate.setDefaultSpawn(world, position);
        worlds.put(world.uid(), Optional.of(position));
    }

    @Override
    public void setNamedSpawn(String name, Position position) {
        delegate.setNamedSpawn(name, position);
        named.put(key(name), Optional.of(position));
    }

    @Override
    public void setMainSpawn(Position position) {
        delegate.setMainSpawn(position);
        main.set(Optional.of(position));
    }

    @Override
    public boolean removeDefaultSpawn(WorldRef world) {
        boolean removed = delegate.removeDefaultSpawn(world);
        worlds.put(world.uid(), Optional.empty());
        return removed;
    }

    @Override
    public void setMirror(SpawnMirror mirror) {
        delegate.setMirror(mirror);
        mirrors.put(mirror.sourceWorld(), Optional.of(mirror));
    }

    private static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
