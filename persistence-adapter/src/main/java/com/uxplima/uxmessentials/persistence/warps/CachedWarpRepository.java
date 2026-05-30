package com.uxplima.uxmessentials.persistence.warps;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.persistence.runtime.ReadThroughCache;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;

/**
 * A Caffeine read-cache decorator over a delegate {@link WarpRepository}. Warps are server-wide and the
 * full set is small and read on nearly every {@code /warp}, {@code /warps}, and tab-complete, so the cache
 * holds the <em>whole</em> warp set under one sentinel key (an ordered name → warp map) rather than caching
 * each name separately. A write to any warp invalidates that one entry, so the next read reloads the full
 * set — write-through at the delegate, invalidate here, never a write-back cache that could lose a mutation.
 * The durable source of truth is always the delegate.
 *
 * <p>{@link #find} and {@link #exists} are served from the cached map, so a {@code /warp} that resolves a
 * name and the {@code /warps} list share one cached read.
 */
public final class CachedWarpRepository implements WarpRepository {

    private static final String ALL_KEY = "all";
    private static final long SINGLE_ENTRY = 1L;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final WarpRepository delegate;
    private final ReadThroughCache<String, Map<String, Warp>> cache;

    public CachedWarpRepository(WarpRepository delegate) {
        this(delegate, DEFAULT_TTL);
    }

    public CachedWarpRepository(WarpRepository delegate, Duration ttl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(ttl, "ttl");
        this.cache = ReadThroughCache.create(key -> loadAll(), SINGLE_ENTRY, ttl);
    }

    @Override
    public Optional<Warp> find(WarpName name) {
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(snapshot().get(name.value()));
    }

    @Override
    public List<Warp> all() {
        return List.copyOf(snapshot().values());
    }

    @Override
    public boolean exists(WarpName name) {
        Objects.requireNonNull(name, "name");
        return snapshot().containsKey(name.value());
    }

    @Override
    public void save(Warp warp) {
        Objects.requireNonNull(warp, "warp");
        delegate.save(warp);
        cache.invalidate(ALL_KEY);
    }

    @Override
    public void delete(WarpName name) {
        Objects.requireNonNull(name, "name");
        delegate.delete(name);
        cache.invalidate(ALL_KEY);
    }

    /** Drop the cached set; call on a module reload. */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    private Map<String, Warp> snapshot() {
        return cache.get(ALL_KEY);
    }

    private Map<String, Warp> loadAll() {
        Map<String, Warp> byName = new LinkedHashMap<>();
        for (Warp warp : delegate.all()) {
            byName.put(warp.name().value(), warp);
        }
        return byName;
    }
}
