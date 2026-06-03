package com.uxplima.uxmessentials.persistence.holograms;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.persistence.runtime.ReadThroughCache;

/**
 * A Caffeine read-cache decorator over a delegate {@link HologramRepository}. Holograms are server-wide and
 * the full set is small and read on enable (spawn-on-enable), on {@code /hologram list}, and on every edit,
 * so the cache holds the <em>whole</em> hologram set under one sentinel key (an ordered name → hologram map)
 * rather than caching each name separately. A write to any hologram invalidates that one entry, so the next
 * read reloads the full set — write-through at the delegate, invalidate here, never a write-back cache that
 * could lose a mutation. The durable source of truth is always the delegate.
 */
public final class CachedHologramRepository implements HologramRepository {

    private static final String ALL_KEY = "all";
    private static final long SINGLE_ENTRY = 1L;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final HologramRepository delegate;
    private final ReadThroughCache<String, Map<String, Hologram>> cache;

    public CachedHologramRepository(HologramRepository delegate) {
        this(delegate, DEFAULT_TTL);
    }

    public CachedHologramRepository(HologramRepository delegate, Duration ttl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(ttl, "ttl");
        this.cache = ReadThroughCache.create(key -> loadAll(), SINGLE_ENTRY, ttl);
    }

    @Override
    public Optional<Hologram> find(HologramName name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(snapshot().get(name.value()));
    }

    @Override
    public List<Hologram> all() {
        return List.copyOf(snapshot().values());
    }

    @Override
    public boolean exists(HologramName name) {
        Objects.requireNonNull(name, "name");
        return snapshot().containsKey(name.value());
    }

    @Override
    public void save(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        delegate.save(hologram);
        cache.invalidate(ALL_KEY);
    }

    @Override
    public void delete(HologramName name) {
        Objects.requireNonNull(name, "name");
        delegate.delete(name);
        cache.invalidate(ALL_KEY);
    }

    /** Drop the cached set; call on a module reload. */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    private Map<String, Hologram> snapshot() {
        return cache.get(ALL_KEY);
    }

    private Map<String, Hologram> loadAll() {
        Map<String, Hologram> byName = new LinkedHashMap<>();
        for (Hologram hologram : delegate.all()) {
            byName.put(hologram.name().value(), hologram);
        }
        return byName;
    }
}
