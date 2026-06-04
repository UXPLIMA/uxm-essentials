package com.uxplima.uxmessentials.persistence.playerwarps;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.ReadThroughCache;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A Caffeine read-cache decorator over a delegate {@link PlayerWarpRepository}, keyed by owner uuid. A load
 * misses through to the database once and is served from memory until a write to that owner invalidates the
 * entry — write-through at the delegate, invalidate here, never a write-back cache that could lose a mutation.
 * The durable source of truth is always the delegate; this only spares repeated reads of a hot owner's small
 * set.
 *
 * <p>The cached value is the whole owner set as an ordered name → warp map, so a {@code /pwarp} that resolves
 * a name, the {@code /setpwarp} count and exists checks, and the {@code /pwarps} list all share one cached
 * read. {@link #count}, {@link #ownedBy}, and {@link #publicOf} are derived from the cached map rather than
 * issuing further queries.
 */
public final class CachedPlayerWarpRepository implements PlayerWarpRepository {

    private static final long DEFAULT_MAX_OWNERS = 10_000L;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final PlayerWarpRepository delegate;
    private final ReadThroughCache<UUID, Map<String, PlayerWarp>> cache;

    public CachedPlayerWarpRepository(PlayerWarpRepository delegate) {
        this(delegate, DEFAULT_MAX_OWNERS, DEFAULT_TTL);
    }

    public CachedPlayerWarpRepository(PlayerWarpRepository delegate, long maximumOwners, Duration ttl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(ttl, "ttl");
        this.cache = ReadThroughCache.create(this::loadFresh, maximumOwners, ttl);
    }

    @Override
    public Optional<PlayerWarp> find(PlayerRef owner, PlayerWarpName name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(snapshot(owner).get(name.value()));
    }

    @Override
    public List<PlayerWarp> ownedBy(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return List.copyOf(snapshot(owner).values());
    }

    @Override
    public List<PlayerWarp> publicOf(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        List<PlayerWarp> shown = new ArrayList<>();
        for (PlayerWarp warp : snapshot(owner).values()) {
            if (warp.isPublic()) {
                shown.add(warp);
            }
        }
        return List.copyOf(shown);
    }

    @Override
    public int count(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return snapshot(owner).size();
    }

    @Override
    public boolean exists(PlayerRef owner, PlayerWarpName name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        return snapshot(owner).containsKey(name.value());
    }

    @Override
    public void save(PlayerWarp warp) {
        Objects.requireNonNull(warp, "warp");
        delegate.save(warp);
        cache.invalidate(warp.owner().uuid());
    }

    @Override
    public void delete(PlayerRef owner, PlayerWarpName name) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        delegate.delete(owner, name);
        cache.invalidate(owner.uuid());
    }

    @Override
    public Optional<List<PlayerWarp>> peekOwned(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return cache.getIfPresent(owner.uuid()).map(byName -> List.copyOf(byName.values()));
    }

    /** Drop every cached owner; call on a module reload. */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * Drop one cached owner so the next read reloads it from the database. Called by the cross-server bus
     * client when a peer reports this owner's player-warps changed on another backend.
     */
    public void invalidateOwner(UUID owner) {
        cache.invalidate(Objects.requireNonNull(owner, "owner"));
    }

    private Map<String, PlayerWarp> snapshot(PlayerRef owner) {
        return cache.get(owner.uuid());
    }

    private Map<String, PlayerWarp> loadFresh(UUID ownerUuid) {
        // The cache key is the owner uuid; the loader needs a PlayerRef, and the delegate only reads the uuid
        // (the row carries no owner name), so a name placeholder here never reaches a row.
        Map<String, PlayerWarp> byName = new LinkedHashMap<>();
        for (PlayerWarp warp : delegate.ownedBy(new PlayerRef(ownerUuid, ownerUuid.toString()))) {
            byName.put(warp.name().value(), warp);
        }
        return byName;
    }
}
