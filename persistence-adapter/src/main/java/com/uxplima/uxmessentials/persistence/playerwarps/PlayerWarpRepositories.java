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
        Objects.requireNonNull(persistence, "persistence");
        return new CachedPlayerWarpRepository(new JooqPlayerWarpRepository(persistence.dsl()));
    }
}
