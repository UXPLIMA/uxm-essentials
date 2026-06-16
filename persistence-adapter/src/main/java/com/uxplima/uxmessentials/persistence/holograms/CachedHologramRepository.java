package com.uxplima.uxmessentials.persistence.holograms;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
 *
 * <p>The MANUAL-visibility viewer set is cached the same way, but keyed by hologram name in a second cache: it
 * is read on every render (so a refreshing MANUAL hologram queries it each refresh tick) and on every join, yet
 * mutated only on {@code /hologram show|hide} — exactly the small, hot-read, rare-write shape a read cache is
 * for. A {@code showTo}/{@code hideFrom}/{@code delete} writes through the delegate then invalidates that name,
 * so the next read reloads the durable set; the immediate show/hide path never serves a stale set because the
 * mutation evicts before it returns. Caching it keeps the render path off a synchronous SQLite read on the tick
 * thread.
 */
public final class CachedHologramRepository implements HologramRepository {

    private static final String ALL_KEY = "all";
    private static final long SINGLE_ENTRY = 1L;
    private static final long MAX_VIEWER_SETS = 512L;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final HologramRepository delegate;
    private final ReadThroughCache<String, Map<String, Hologram>> cache;
    private final ReadThroughCache<String, Set<UUID>> viewers;

    public CachedHologramRepository(HologramRepository delegate) {
        this(delegate, DEFAULT_TTL);
    }

    public CachedHologramRepository(HologramRepository delegate, Duration ttl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(ttl, "ttl");
        this.cache = ReadThroughCache.create(key -> loadAll(), SINGLE_ENTRY, ttl);
        this.viewers =
                ReadThroughCache.create(name -> delegate.manualViewers(HologramName.of(name)), MAX_VIEWER_SETS, ttl);
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
        viewers.invalidate(name.value());
    }

    @Override
    public Set<UUID> manualViewers(HologramName name) {
        Objects.requireNonNull(name, "name");
        // Served from memory after the first load: read on every render (each refresh tick of a refreshing
        // MANUAL hologram) and on every join, so a synchronous SQLite read here would land on the tick thread.
        // A show/hide/delete invalidates this name, so the next read reloads the durable set.
        return viewers.get(name.value());
    }

    @Override
    public void showTo(HologramName name, UUID viewer) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(viewer, "viewer");
        delegate.showTo(name, viewer);
        viewers.invalidate(name.value());
    }

    @Override
    public void hideFrom(HologramName name, UUID viewer) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(viewer, "viewer");
        delegate.hideFrom(name, viewer);
        viewers.invalidate(name.value());
    }

    /** Drop the cached sets; call on a module reload. */
    public void invalidateAll() {
        cache.invalidateAll();
        viewers.invalidateAll();
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
