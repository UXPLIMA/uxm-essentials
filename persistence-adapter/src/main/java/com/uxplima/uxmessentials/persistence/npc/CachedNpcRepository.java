package com.uxplima.uxmessentials.persistence.npc;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.persistence.runtime.ReadThroughCache;

/**
 * A Caffeine read-cache decorator over a delegate {@link NpcRepository}. NPCs are server-wide and the full set
 * is small and read on enable (spawn-on-enable), on {@code /npc list}, on every player join (which NPCs to show
 * the joiner), and on every edit, so the cache holds the <em>whole</em> NPC set under one sentinel key (an
 * ordered name → npc map) rather than caching each name separately. A write to any NPC invalidates that one
 * entry, so the next read reloads the full set — write-through at the delegate, invalidate here, never a
 * write-back cache that could lose a mutation. The durable source of truth is always the delegate.
 */
public final class CachedNpcRepository implements NpcRepository {

    private static final String ALL_KEY = "all";
    private static final long SINGLE_ENTRY = 1L;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final NpcRepository delegate;
    private final ReadThroughCache<String, Map<String, Npc>> cache;

    public CachedNpcRepository(NpcRepository delegate) {
        this(delegate, DEFAULT_TTL);
    }

    public CachedNpcRepository(NpcRepository delegate, Duration ttl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(ttl, "ttl");
        this.cache = ReadThroughCache.create(key -> loadAll(), SINGLE_ENTRY, ttl);
    }

    @Override
    public Optional<Npc> find(NpcName name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(snapshot().get(name.value()));
    }

    @Override
    public List<Npc> all() {
        return List.copyOf(snapshot().values());
    }

    @Override
    public boolean exists(NpcName name) {
        Objects.requireNonNull(name, "name");
        return snapshot().containsKey(name.value());
    }

    @Override
    public void save(Npc npc) {
        Objects.requireNonNull(npc, "npc");
        delegate.save(npc);
        cache.invalidate(ALL_KEY);
    }

    @Override
    public void delete(NpcName name) {
        Objects.requireNonNull(name, "name");
        delegate.delete(name);
        cache.invalidate(ALL_KEY);
    }

    /** Drop the cached set; call on a module reload. */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    private Map<String, Npc> snapshot() {
        return cache.get(ALL_KEY);
    }

    private Map<String, Npc> loadAll() {
        Map<String, Npc> byName = new LinkedHashMap<>();
        for (Npc npc : delegate.all()) {
            byName.put(npc.name().value(), npc);
        }
        return byName;
    }
}
