package com.uxplima.uxmessentials.persistence.worlds;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Read-cache decorator: an in-memory snapshot warmed on first read, write-through with CAS refresh. */
public final class CachedWorldRepository implements WorldRepository {

    private final WorldRepository delegate;
    private final AtomicReference<Map<String, ManagedWorld>> loaded = new AtomicReference<>();

    public CachedWorldRepository(WorldRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public Optional<ManagedWorld> find(WorldName name) {
        return Optional.ofNullable(index().get(name.value()));
    }

    @Override
    public List<ManagedWorld> all() {
        return List.copyOf(index().values());
    }

    @Override
    public boolean exists(WorldName name) {
        return index().containsKey(name.value());
    }

    @Override
    public void save(ManagedWorld world) {
        delegate.save(world);
        republish(next -> next.put(world.name().value(), world));
    }

    @Override
    public void delete(WorldName name) {
        delegate.delete(name);
        republish(next -> next.remove(name.value()));
    }

    /** Force a reload on the next read (e.g. after enable-time reconciliation mutated rows directly). */
    public void invalidateAll() {
        loaded.set(null);
    }

    private Map<String, ManagedWorld> index() {
        Map<String, ManagedWorld> current = loaded.get();
        if (current != null) {
            return current;
        }
        Map<String, ManagedWorld> fresh = loadAll();
        if (loaded.compareAndSet(null, fresh)) {
            return fresh;
        }
        // A racing load won; reuse its published set so a later write applies to the live snapshot, not ours.
        Map<String, ManagedWorld> winner = loaded.get();
        return winner != null ? winner : fresh;
    }

    /** Publish a fresh set with {@code edit} applied, retrying if a racing load or write swapped under us. */
    private void republish(Consumer<Map<String, ManagedWorld>> edit) {
        while (true) {
            Map<String, ManagedWorld> current = index();
            Map<String, ManagedWorld> next = new LinkedHashMap<>(current);
            edit.accept(next);
            if (loaded.compareAndSet(current, next)) {
                return;
            }
        }
    }

    private Map<String, ManagedWorld> loadAll() {
        Map<String, ManagedWorld> map = new LinkedHashMap<>();
        for (ManagedWorld world : delegate.all()) {
            map.put(world.name().value(), world);
        }
        return map;
    }
}
