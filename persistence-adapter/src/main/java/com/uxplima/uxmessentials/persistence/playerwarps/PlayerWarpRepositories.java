package com.uxplima.uxmessentials.persistence.playerwarps;

import java.util.Objects;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Factory for the player-warps context's persistence adapter, so the consuming bukkit-adapter wires a
 * {@link PlayerWarpRepository} from the {@link Persistence} handle it already holds without ever naming a jOOQ
 * type (jOOQ is an {@code implementation} dependency of this module, kept off the consumer's compile
 * classpath). The returned repository is the cached jOOQ adapter — write-through at the database, invalidate
 * in the Caffeine cache.
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
}
