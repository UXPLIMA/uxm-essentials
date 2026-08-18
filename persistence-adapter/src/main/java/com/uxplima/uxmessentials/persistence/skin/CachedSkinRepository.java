package com.uxplima.uxmessentials.persistence.skin;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;

/**
 * A Caffeine read-cache decorator over a delegate {@link SkinRepository}, keyed by the wearer.
 *
 * <p>A stored skin is read far more often than it is written: every login consults it, and so does every HUD
 * refresh that shows what somebody is wearing. Write-through at the delegate, invalidate here: a save or a delete
 * drops that player's entry and nothing else, so the durable row is always the source of truth and no mutation can
 * be lost to the cache.
 */
public final class CachedSkinRepository implements SkinRepository {

    private static final long DEFAULT_MAX_PLAYERS = 10_000L;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final SkinRepository delegate;
    private final Cache<UUID, Optional<PlayerSkin>> cache;

    public CachedSkinRepository(SkinRepository delegate) {
        this(delegate, DEFAULT_MAX_PLAYERS, DEFAULT_TTL);
    }

    public CachedSkinRepository(SkinRepository delegate, long maximumPlayers, Duration ttl) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(ttl, "ttl");
        this.cache = Caffeine.newBuilder()
                .maximumSize(maximumPlayers)
                .expireAfterWrite(ttl)
                .build();
    }

    @Override
    public Optional<PlayerSkin> find(UUID player) {
        Objects.requireNonNull(player, "player");
        return cache.get(player, delegate::find);
    }

    @Override
    public void save(PlayerSkin skin) {
        Objects.requireNonNull(skin, "skin");
        delegate.save(skin);
        cache.invalidate(skin.owner().uuid());
    }

    @Override
    public void delete(UUID player) {
        Objects.requireNonNull(player, "player");
        delegate.delete(player);
        cache.invalidate(player);
    }
}
