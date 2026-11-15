package com.uxplima.uxmessentials.persistence.worlds;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.persistence.runtime.SnapshotSwap;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/**
 * Read-cache decorator: an in-memory snapshot of every world of the network, warmed on first read, write-through with
 * CAS refresh. The snapshot is keyed by server and name, so this backend's view and the network view are both served
 * from it without a second load.
 */
public final class CachedWorldRepository implements WorldRepository {

    private final WorldRepository delegate;
    private final String server;
    private final AtomicReference<Map<String, NetworkWorld>> loaded = new AtomicReference<>();

    /** {@code server} is the backend {@code delegate} reads and writes for. */
    public CachedWorldRepository(WorldRepository delegate, String server) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public Optional<ManagedWorld> find(WorldName name) {
        return Optional.ofNullable(index().get(key(server, name.value()))).map(NetworkWorld::world);
    }

    @Override
    public List<ManagedWorld> all() {
        return index().values().stream()
                .filter(world -> world.livesOn(server))
                .map(NetworkWorld::world)
                .toList();
    }

    @Override
    public boolean exists(WorldName name) {
        return index().containsKey(key(server, name.value()));
    }

    @Override
    public void save(ManagedWorld world) {
        delegate.save(world);
        republish(next -> next.put(key(server, world.name().value()), new NetworkWorld(server, world)));
    }

    @Override
    public void delete(WorldName name) {
        delegate.delete(name);
        republish(next -> {
            next.remove(key(server, name.value()));
            next.remove(key(NetworkWorld.UNSCOPED, name.value()));
        });
    }

    @Override
    public List<NetworkWorld> network() {
        return List.copyOf(index().values());
    }

    /**
     * Load the whole set again and swap it in, for rows another server wrote. Readers keep the old set until the
     * swap, so none of them loads on its own thread meanwhile; a local write during the load wins over it. Blocks on
     * the database, so call it off the tick.
     */
    public Optional<SnapshotSwap<NetworkWorld>> reloadAll() {
        return SnapshotSwap.reload(loaded, this::loadAll);
    }

    /** Force a reload on the next read. */
    public void invalidateAll() {
        loaded.set(null);
    }

    private Map<String, NetworkWorld> index() {
        Map<String, NetworkWorld> current = loaded.get();
        if (current != null) {
            return current;
        }
        Map<String, NetworkWorld> fresh = loadAll();
        if (loaded.compareAndSet(null, fresh)) {
            return fresh;
        }
        // A racing load won; reuse its published set so a later write applies to the live snapshot, not ours.
        Map<String, NetworkWorld> winner = loaded.get();
        return winner != null ? winner : fresh;
    }

    /** Publish a fresh set with {@code edit} applied, retrying if a racing load or write swapped under us. */
    private void republish(Consumer<Map<String, NetworkWorld>> edit) {
        while (true) {
            Map<String, NetworkWorld> current = index();
            Map<String, NetworkWorld> next = new LinkedHashMap<>(current);
            edit.accept(next);
            if (loaded.compareAndSet(current, next)) {
                return;
            }
        }
    }

    private Map<String, NetworkWorld> loadAll() {
        Map<String, NetworkWorld> map = new LinkedHashMap<>();
        for (NetworkWorld world : delegate.network()) {
            map.put(key(world.server(), world.name().value()), world);
        }
        return map;
    }

    /** Server names never hold a NUL, so the pair cannot collide with another. */
    private static String key(String server, String name) {
        return server + '\u0000' + name;
    }
}
