package com.uxplima.uxmessentials.persistence.teleport;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.SpawnDirectory;
import com.uxplima.uxmessentials.teleport.domain.SpawnMirror;
import org.jspecify.annotations.Nullable;

/**
 * Write-through spawn cache. The complete durable set is warmed at enable so command, join and respawn region
 * threads never query the database; writes update the durable store first and then publish the new cached value.
 *
 * <p>{@link #warmAll()} also runs again later, off the tick, to take in rows another server wrote. A reader during
 * that reload sees the old or the new value of a key, never a gap, and a reload whose snapshot a local write
 * overtook is dropped rather than applied over the write.
 */
public final class CachedSpawnDirectory implements SpawnDirectory {

    private final SpawnDirectory delegate;
    private final Supplier<Optional<SpawnDirectorySnapshot>> snapshotLoader;
    private final ConcurrentHashMap<UUID, Optional<Position>> worlds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Optional<SpawnMirror>> mirrors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Optional<Position>> named = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Optional<Position>> mains = new ConcurrentHashMap<>();
    private volatile boolean authoritative;
    // Guards publishing to the maps, so a reload checks for overtaking writes and applies in one step.
    private final Object publish = new Object();
    private long writes;

    public CachedSpawnDirectory(SpawnDirectory delegate) {
        this(delegate, Optional::empty);
    }

    CachedSpawnDirectory(SpawnDirectory delegate, Supplier<Optional<SpawnDirectorySnapshot>> snapshotLoader) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.snapshotLoader = Objects.requireNonNull(snapshotLoader, "snapshotLoader");
    }

    /** Load the complete durable set; after this call even absent-key reads are memory-only. */
    public synchronized void warmAll() {
        long writesBefore;
        synchronized (publish) {
            writesBefore = writes;
        }
        Optional<SpawnDirectorySnapshot> loaded = snapshotLoader.get();
        if (loaded.isEmpty()) {
            return;
        }
        SpawnDirectorySnapshot snapshot = loaded.get();
        synchronized (publish) {
            if (writes != writesBefore) {
                // A local write is newer than this snapshot; the next reload takes in both.
                return;
            }
            replace(worlds, snapshot.worlds());
            replace(named, snapshot.named());
            replace(mirrors, snapshot.mirrors());
            replace(mains, snapshot.mains());
            authoritative = true;
        }
    }

    private static <K, V> void replace(ConcurrentHashMap<K, Optional<V>> cache, Map<K, V> fresh) {
        cache.keySet().retainAll(fresh.keySet());
        fresh.forEach((key, value) -> cache.put(key, Optional.of(value)));
    }

    private void published(Runnable put) {
        synchronized (publish) {
            put.run();
            writes++;
        }
    }

    /** Warm every spawn fact needed by automatic join and death handling. */
    public void warm(Collection<WorldRef> loadedWorlds) {
        Objects.requireNonNull(loadedWorlds, "loadedWorlds");
        mainSpawn(null);
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
    public Optional<Position> mainSpawn(@Nullable ServerScope scope) {
        String key = SpawnDirectorySnapshot.mainKey(scope);
        Optional<Position> cached = mains.get(key);
        if (cached != null || authoritative) {
            return cached == null ? Optional.empty() : cached;
        }
        return mains.computeIfAbsent(key, ignored -> delegate.mainSpawn(scope));
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
        published(() -> worlds.put(world.uid(), Optional.of(position)));
    }

    @Override
    public void setNamedSpawn(String name, Position position) {
        delegate.setNamedSpawn(name, position);
        published(() -> named.put(key(name), Optional.of(position)));
    }

    @Override
    public void setMainSpawn(@Nullable ServerScope scope, Position position) {
        delegate.setMainSpawn(scope, position);
        published(() -> mains.put(SpawnDirectorySnapshot.mainKey(scope), Optional.of(position)));
    }

    @Override
    public boolean removeDefaultSpawn(WorldRef world) {
        boolean removed = delegate.removeDefaultSpawn(world);
        published(() -> worlds.put(world.uid(), Optional.empty()));
        return removed;
    }

    @Override
    public void setMirror(SpawnMirror mirror) {
        delegate.setMirror(mirror);
        published(() -> mirrors.put(mirror.sourceWorld(), Optional.of(mirror)));
    }

    private static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
