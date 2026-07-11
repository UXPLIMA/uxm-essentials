package com.uxplima.uxmessentials.persistence.playerwarps;

import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpPasswordStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpBanStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpFavouriteStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpRatingStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpWhitelistStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the player-warps context's persistence adapter, so the consuming bukkit-adapter wires the
 * {@link PlayerWarpRepository} and the context's social stores (bans, members, whitelist, password) from the
 * {@link Persistence} handle it already holds without ever naming a jOOQ type (jOOQ is an {@code implementation}
 * dependency of this module, kept off the consumer's compile classpath). The returned repository is the cached
 * jOOQ adapter — write-through at the database, invalidate in the Caffeine cache; the stores are the plain jOOQ
 * adapters keyed by the warp's surrogate id.
 */
@NullMarked
public final class PlayerWarpRepositories {

    private PlayerWarpRepositories() {}

    /** A cached jOOQ {@link PlayerWarpRepository} over the shared persistence DSL. */
    public static PlayerWarpRepository cached(Persistence persistence) {
        return cached(persistence, java.util.UUID::toString);
    }

    /**
     * As {@link #cached(Persistence)} but resolving each warp owner's display name through {@code names} (an
     * adapter-supplied uuid-to-name profile lookup), so the player-warp carries the live owner name instead of
     * the uuid string. The display wiring passes a real resolver; non-display callers keep the uuid default.
     */
    public static PlayerWarpRepository cached(
            Persistence persistence, java.util.function.Function<java.util.UUID, String> names) {
        return cachedConcrete(persistence, names);
    }

    /**
     * As {@link #cached(Persistence, java.util.function.Function)} but returned as its concrete decorator type, so
     * the wiring can hand the cross-server bus a per-owner invalidation hook on the same cache the {@code /pwarp}
     * commands read — a remote {@code /setpwarp} drops exactly that owner's cached set. Same backing as
     * {@link #cached}; this overload exposes the decorator only so the invalidation seam can reach it.
     */
    public static CachedPlayerWarpRepository cachedConcrete(
            Persistence persistence, java.util.function.Function<java.util.UUID, String> names) {
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(names, "names");
        return new CachedPlayerWarpRepository(new JooqPlayerWarpRepository(persistence.dsl(), names));
    }

    /** The jOOQ {@link WarpBanStore} — one row per {@code (warp, player)}, active-at expiry evaluated in-store. */
    public static WarpBanStore banStore(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqWarpBanStore(persistence.dsl());
    }

    /** The jOOQ {@link WarpMemberStore} — the co-owner/manager roster the access gate reads roles from. */
    public static WarpMemberStore memberStore(Persistence persistence, Logger log) {
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(log, "log");
        return new JooqWarpMemberStore(persistence.dsl(), log);
    }

    /** The jOOQ {@link WarpWhitelistStore} — the allow-list a whitelist-access warp gates on. */
    public static WarpWhitelistStore whitelistStore(Persistence persistence, Clock clock) {
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(clock, "clock");
        return new JooqWarpWhitelistStore(persistence.dsl(), clock);
    }

    /** The jOOQ {@link WarpRatingStore} — the per-vote star rows the rate use case tallies the rollup from. */
    public static WarpRatingStore ratingStore(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqWarpRatingStore(persistence.dsl());
    }

    /** The jOOQ {@link WarpFavouriteStore} — a player's starred warps, its rows the source of {@code favourite_count}. */
    public static WarpFavouriteStore favouriteStore(Persistence persistence, Clock clock) {
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(clock, "clock");
        return new JooqWarpFavouriteStore(persistence.dsl(), clock);
    }

    /**
     * The jOOQ {@link PlayerWarpPasswordStore} over the shipped PBKDF2 hasher — the plaintext is hashed inside the
     * store and only the digest is ever written; the application and domain never hold a secret.
     */
    public static PlayerWarpPasswordStore passwordStore(Persistence persistence) {
        Objects.requireNonNull(persistence, "persistence");
        return new JooqPlayerWarpPasswordStore(persistence.dsl(), new Pbkdf2PasswordHasher());
    }
}
