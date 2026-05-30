package com.uxplima.uxmessentials.persistence.homes;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.persistence.runtime.ReadThroughCache;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A Caffeine read-cache decorator over a delegate {@link HomeRepository}, keyed by owner uuid. A load
 * misses through to the database once and is served from memory until a write to that owner invalidates
 * the entry — write-through at the delegate, invalidate here, never a write-back cache that could lose a
 * mutation. The durable source of truth is always the delegate; this only spares repeated reads of a hot
 * owner's small set.
 *
 * <p>The cached value is the whole {@link HomeSet}, so a {@code /home} that needs the owner's homes and the
 * {@code /sethome} count check share one cached read. {@link #count} is derived from the cached set rather
 * than issuing a second {@code COUNT(*)}.
 */
public final class CachedHomeRepository implements HomeRepository {

    private static final long DEFAULT_MAX_OWNERS = 10_000L;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final HomeRepository delegate;
    private final ReadThroughCache<UUID, HomeSet> cache;

    public CachedHomeRepository(HomeRepository delegate) {
        this(delegate, DEFAULT_MAX_OWNERS, DEFAULT_TTL);
    }

    public CachedHomeRepository(HomeRepository delegate, long maximumOwners, Duration ttl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(ttl, "ttl");
        this.cache = ReadThroughCache.create(this::loadFresh, maximumOwners, ttl);
    }

    @Override
    public HomeSet load(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return cache.get(owner.uuid());
    }

    @Override
    public int count(PlayerRef owner) {
        return load(owner).count();
    }

    @Override
    public void save(Home home) {
        Objects.requireNonNull(home, "home");
        delegate.save(home);
        cache.invalidate(home.owner().uuid());
    }

    @Override
    public void rename(PlayerRef owner, HomeName from, HomeName to) {
        delegate.rename(owner, from, to);
        cache.invalidate(Objects.requireNonNull(owner, "owner").uuid());
    }

    @Override
    public void delete(PlayerRef owner, HomeName name) {
        delegate.delete(owner, name);
        cache.invalidate(Objects.requireNonNull(owner, "owner").uuid());
    }

    /** Drop every cached owner; call on a module reload. */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    private HomeSet loadFresh(UUID ownerUuid) {
        // The cache key is the owner uuid; the loader needs a PlayerRef, and the delegate only reads the
        // uuid (the row carries no owner name), so a name placeholder here never reaches a row.
        return delegate.load(new PlayerRef(ownerUuid, ownerUuid.toString()));
    }
}
